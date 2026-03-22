/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 小米便签数据库管理核心类
 * 继承SQLiteOpenHelper，负责note.db数据库的创建、版本升级、表结构初始化、触发器管理
 * 核心职责：
 * 1. 初始化Note/Data两张核心表的结构
 * 2. 创建维护数据一致性的触发器（如文件夹计数、便签内容同步）
 * 3. 初始化系统文件夹（根目录、回收站、通话记录等）
 * 4. 处理数据库版本升级，保证数据兼容
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名
    private static final String DB_NAME = "note.db";
    // 数据库版本号（升级时递增，触发onUpgrade）
    private static final int DB_VERSION = 4;

    /**
     * 数据库表名常量接口
     * 统一管理表名，避免硬编码
     */
    public interface TABLE {
        public static final String NOTE = "note";  // 便签/文件夹主表
        public static final String DATA = "data";  // 便签详情数据表
    }

    // 日志标签：用于Logcat调试定位
    private static final String TAG = "NotesDatabaseHelper";
    // 单例实例：保证全应用只有一个数据库助手实例，避免多实例导致的数据库锁问题
    private static NotesDatabaseHelper mInstance;

    // ------------------------------ 表创建SQL常量 ------------------------------
    /**
     * Note表（便签/文件夹主表）创建SQL
     * 字段默认值：使用strftime('%s','now') * 1000获取当前时间戳（秒转毫秒）
     * 主键：ID（自增，系统文件夹手动指定负数ID）
     */
    private static final String CREATE_NOTE_TABLE_SQL =
            "CREATE TABLE " + TABLE.NOTE + "(" +
                    NoteColumns.ID + " INTEGER PRIMARY KEY," +                // 主键ID
                    NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +  // 父文件夹ID
                    NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," + // 提醒时间戳
                    NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," + // 背景色ID
                    // 创建时间：默认当前时间戳（秒转毫秒）
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," + // 是否有附件
                    // 修改时间：默认当前时间戳
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," + // 文件夹下便签数
                    NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +      // 便签内容/文件夹名
                    NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +       // 类型（便签/文件夹）
                    NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +  // 关联的Widget ID
                    NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," + // Widget类型
                    NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +    // 同步ID
                    NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," + // 本地修改标识
                    NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," + // 原始父文件夹ID
                    NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +     // Google Task ID
                    NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +     // 版本号
                    ")";

    /**
     * Data表（便签详情表）创建SQL
     * 存储便签的具体内容（文本/通话记录），与Note表通过NOTE_ID关联
     */
    private static final String CREATE_DATA_TABLE_SQL =
            "CREATE TABLE " + TABLE.DATA + "(" +
                    DataColumns.ID + " INTEGER PRIMARY KEY," +                // 主键ID
                    DataColumns.MIME_TYPE + " TEXT NOT NULL," +               // MIME类型（区分文本/通话记录）
                    DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +    // 关联的Note ID
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 创建时间
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 修改时间
                    DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +      // 核心内容
                    DataColumns.DATA1 + " INTEGER," +                         // 通用整型列1
                    DataColumns.DATA2 + " INTEGER," +                         // 通用整型列2
                    DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +        // 通用文本列3
                    DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +        // 通用文本列4
                    DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +         // 通用文本列5
                    ")";

    /**
     * Data表索引创建SQL：基于NOTE_ID创建索引
     * 作用：优化按NOTE_ID查询Data表的性能（核心关联查询）
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON " +
                    TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ------------------------------ Note表触发器SQL（维护文件夹计数/数据一致性） ------------------------------
    /**
     * 触发器：更新便签父文件夹时，新增文件夹的便签数+1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_update "+
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器：更新便签父文件夹时，原文件夹的便签数-1（确保计数≥0）
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
                    " END";

    /**
     * 触发器：插入新便签时，对应文件夹的便签数+1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_insert " +
                    " AFTER INSERT ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器：删除便签时，对应文件夹的便签数-1（确保计数≥0）
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
                    " END";

    // ------------------------------ Data表触发器SQL（同步便签内容） ------------------------------
    /**
     * 触发器：插入文本便签Data时，同步更新Note表的SNIPPET字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER update_note_content_on_insert " +
                    " AFTER INSERT ON " + TABLE.DATA +
                    " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：更新文本便签Data时，同步更新Note表的SNIPPET字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_update " +
                    " AFTER UPDATE ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：删除文本便签Data时，清空Note表的SNIPPET字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_delete " +
                    " AFTER delete ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=''" +
                    "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
                    " END";

    // ------------------------------ 级联操作触发器SQL ------------------------------
    /**
     * 触发器：删除Note时，级联删除关联的Data记录（避免数据残留）
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
            "CREATE TRIGGER delete_data_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.DATA +
                    "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器：删除文件夹时，级联删除该文件夹下的所有便签
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
            "CREATE TRIGGER folder_delete_notes_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.NOTE +
                    "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器：文件夹被移入回收站时，其下所有便签也移入回收站
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
            "CREATE TRIGGER folder_move_notes_on_trash " +
                    " AFTER UPDATE ON " + TABLE.NOTE +
                    " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";
    // ------------------------------ 构造方法 & 单例 ------------------------------
    /**
     * 构造方法：初始化SQLiteOpenHelper
     * @param context 上下文（建议传入Application Context避免内存泄漏）
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建Note表 + 触发器 + 系统文件夹
     * @param db 数据库实例
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);      // 创建表结构
        reCreateNoteTableTriggers(db);          // 创建/重建触发器
        createSystemFolder(db);                 // 初始化系统文件夹
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重建Note表所有触发器（先删后建，避免重复创建）
     * @param db 数据库实例
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 先删除旧触发器（避免重复创建报错）
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 初始化系统文件夹（手动插入系统级文件夹记录，ID为预定义负数）
     * @param db 数据库实例
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        /**
         * 1. 通话记录文件夹：存储通话记录类便签
         */
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 2. 根文件夹：默认文件夹（所有普通便签默认归属）
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 3. 临时文件夹：用于便签移动过程中的临时归属
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 4. 回收站文件夹：存储删除的便签
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建Data表 + 触发器 + 索引
     * @param db 数据库实例
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);      // 创建表结构
        reCreateDataTableTriggers(db);          // 创建/重建触发器
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL); // 创建NOTE_ID索引
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重建Data表所有触发器（先删后建）
     * @param db 数据库实例
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 删除旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        // 创建新触发器
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取单例实例（线程安全）
     * @param context 上下文
     * @return 数据库助手单例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    // ------------------------------ SQLiteOpenHelper 重写方法 ------------------------------
    /**
     * 数据库首次创建时调用：初始化Note/Data表
     * @param db 可写的数据库实例
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 数据库版本升级时调用：处理不同版本间的结构变更
     * @param db 数据库实例
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;  // 是否需要重建触发器
        boolean skipV2 = false;            // 是否跳过V2升级（V1→V2已包含V2→V3）

        // V1 → V2：删除旧表，重建新表（全量更新）
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // V1→V2已包含V2→V3的逻辑
            oldVersion++;
        }

        // V2 → V3：删除无用触发器，新增GTASK_ID字段，添加回收站文件夹
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true; // 需要重建触发器
            oldVersion++;
        }

        // V3 → V4：新增VERSION字段
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 按需重建触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 版本升级校验：确保最终版本与目标版本一致
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    // ------------------------------ 版本升级具体实现 ------------------------------
    /**
     * 升级到V2：删除旧表，重建新表（全量更新，会丢失数据）
     * @param db 数据库实例
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级到V3：
     * 1. 删除无用触发器
     * 2. 新增GTASK_ID字段
     * 3. 添加回收站系统文件夹
     * @param db 数据库实例
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除无用的修改时间触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");

        // 新增GTASK_ID字段（Google Task同步）
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");

        // 插入回收站系统文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级到V4：新增VERSION字段（数据版本管理）
     * @param db 数据库实例
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}