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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 小米便签 ContentProvider 核心实现类
 * 作用：对外暴露便签数据的 CRUD 接口，封装数据库底层操作，实现数据访问的统一入口
 * 核心能力：
 * 1. 解析不同 URI 路由到对应的数据库操作（便签/数据/搜索）
 * 2. 支持系统搜索框架，提供便签搜索和搜索建议功能
 * 3. 数据变更时发送通知，保证 UI 与数据同步
 * 4. 维护便签版本号，支持数据同步校验
 */
public class NotesProvider extends ContentProvider {
    // URI 匹配器：核心组件，用于解析外部传入的 URI 并匹配到对应的操作类型
    private static final UriMatcher mMatcher;
    // 数据库助手实例：复用单例，避免多实例导致的数据库锁冲突
    private NotesDatabaseHelper mHelper;
    // 日志标签：用于 Logcat 调试定位
    private static final String TAG = "NotesProvider";

    // ------------------------------ URI 匹配码常量 ------------------------------
    private static final int URI_NOTE            = 1;      // 匹配：便签列表 URI（content://micode_notes/note）
    private static final int URI_NOTE_ITEM       = 2;      // 匹配：单条便签 URI（content://micode_notes/note/123）
    private static final int URI_DATA            = 3;      // 匹配：便签数据列表 URI（content://micode_notes/data）
    private static final int URI_DATA_ITEM       = 4;      // 匹配：单条便签数据 URI（content://micode_notes/data/123）
    private static final int URI_SEARCH          = 5;      // 匹配：便签搜索 URI（content://micode_notes/search）
    private static final int URI_SEARCH_SUGGEST  = 6;      // 匹配：系统搜索建议 URI

    // 静态代码块：初始化 URI 匹配规则（程序启动时执行一次）
    static {
        // 初始化 UriMatcher，默认匹配码为 NO_MATCH（无匹配）
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // 注册便签相关 URI 规则
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);          // 便签列表
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);   // 单条便签（# 匹配任意数字 ID）

        // 注册便签数据相关 URI 规则
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);          // 数据列表
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);   // 单条数据

        // 注册搜索相关 URI 规则
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);      // 自定义搜索入口
        // 系统搜索建议 URI（兼容无参数/带参数两种格式）
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索结果投影字段定义（适配 Android 系统 SearchManager 规范）
     * 关键说明：
     * 1. x'0A' 是 SQLite 中 '\n' 的十六进制表示，通过 REPLACE 去除换行符，TRIM 去除首尾空格
     * 2. 字段别名映射系统搜索框架的标准字段，确保搜索结果能被系统搜索界面正确展示
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            // 便签 ID → 搜索结果跳转时的额外数据
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            // 便签内容（去换行/空格）→ 搜索结果主文本
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            // 便签内容（去换行/空格）→ 搜索结果副文本
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            // 搜索结果图标 → 使用应用内 search_result 图标
            + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            // 点击搜索结果的意图 → 查看（ACTION_VIEW）
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            // 意图数据类型 → 文本便签的 MIME 类型
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 便签搜索核心 SQL 语句
     * 查询条件：
     * 1. SNIPPET（便签内容）包含搜索关键词（LIKE 模糊匹配）
     * 2. 排除回收站中的便签（PARENT_ID ≠ 回收站 ID）
     * 3. 仅查询普通便签（TYPE = TYPE_NOTE），排除文件夹
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    // ------------------------------ ContentProvider 生命周期方法 ------------------------------
    /**
     * ContentProvider 创建时初始化（仅执行一次）
     * @return 初始化成功返回 true，失败返回 false
     */
    @Override
    public boolean onCreate() {
        // 获取数据库助手单例（全局唯一）
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    // ------------------------------ 核心方法：查询（query） ------------------------------
    /**
     * 对外提供数据查询接口，所有外部查询便签数据的请求都会走这个方法
     * @param uri 目标 URI（指定查询类型：便签列表/单条便签/搜索等）
     * @param projection 需要查询的字段（null 表示查询所有字段）
     * @param selection 查询条件（SQL WHERE 子句，不含 WHERE 关键字）
     * @param selectionArgs 查询条件参数（替换 selection 中的 ? 占位符）
     * @param sortOrder 排序规则（SQL ORDER BY 子句，不含 ORDER BY 关键字）
     * @return 查询结果游标 Cursor（外部需自行关闭，避免内存泄漏）
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null; // 存储查询结果
        // 获取只读数据库实例（查询操作使用只读库，提升性能）
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null; // 存储 URI 中的 ID 片段（单条数据/便签时使用）

        // 根据 URI 匹配码分发查询逻辑
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询便签列表：直接调用数据库 query 方法
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 查询单条便签：解析 URI 中的 ID，拼接查询条件
                id = uri.getPathSegments().get(1); // 获取 URI 路径中第 2 个片段（note/123 中的 123）
                c = db.query(TABLE.NOTE, projection,
                        NoteColumns.ID + "=" + id + parseSelection(selection), // 拼接 ID 条件 + 自定义条件
                        selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询便签数据列表：直接调用数据库 query 方法
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA_ITEM:
                // 查询单条便签数据：解析 URI 中的 ID，拼接查询条件
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection,
                        DataColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索/搜索建议：校验参数合法性（禁止指定排序/投影字段）
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection with this query");
                }

                // 解析搜索关键词（两种来源）
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 搜索建议：从 URI 路径获取关键词（如 search_suggest/test）
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 自定义搜索：从 URI 查询参数获取关键词（如 search?pattern=test）
                    searchString = uri.getQueryParameter("pattern");
                }

                // 关键词为空则返回 null
                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                // 执行模糊搜索：关键词前后拼接 %，实现 LIKE 模糊匹配
                try {
                    searchString = String.format("%%%s%%", searchString); // test → %test%
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY, new String[] { searchString });
                } catch (IllegalStateException ex) {
                    // 捕获数据库异常，打印日志
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default:
                // 未知 URI：抛出异常，提示非法请求
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 注册数据观察者：当查询结果对应的 URI 数据变更时，通知 ContentResolver 刷新 UI
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }
    // ------------------------------ 核心方法：插入（insert） ------------------------------
    /**
     * 对外提供数据插入接口，所有外部插入便签数据的请求都会走这个方法
     * @param uri 目标 URI（指定插入类型：便签/数据）
     * @param values 待插入的数据（ContentValues 封装键值对）
     * @return 插入成功后返回包含新记录 ID 的 URI（如 content://micode_notes/note/123）
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // 获取可写数据库实例（插入操作需要写权限）
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0; // 存储插入的记录 ID

        // 根据 URI 匹配码分发插入逻辑
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入便签：调用数据库 insert 方法，返回便签 ID
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 插入便签数据：先校验是否包含 NOTE_ID（关联的便签 ID）
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    // 缺少 NOTE_ID 打印警告日志
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                // 插入数据，返回数据 ID
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 发送数据变更通知：通知 ContentResolver 数据已更新，触发 UI 刷新
        // 1. 便签数据变更通知（关联的便签 URI）
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        // 2. 数据记录变更通知（关联的数据 URI）
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        // 返回插入后的资源 URI（将新 ID 拼接在原 URI 后）
        return ContentUris.withAppendedId(uri, insertedId);
    }

    // ------------------------------ 核心方法：删除（delete） ------------------------------
    /**
     * 对外提供数据删除接口，所有外部删除便签数据的请求都会走这个方法
     * @param uri 目标 URI（指定删除类型：便签列表/单条便签/数据等）
     * @param selection 删除条件（SQL WHERE 子句）
     * @param selectionArgs 删除条件参数
     * @return 成功删除的行数
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0; // 存储删除的行数
        String id = null;
        // 获取可写数据库实例
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false; // 标记是否删除的是 DATA 表数据

        // 根据 URI 匹配码分发删除逻辑
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除便签列表：追加条件，排除系统文件夹（ID > 0）
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 删除单条便签：解析 ID 并校验（系统文件夹 ID ≤ 0 禁止删除）
                id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break; // 系统文件夹，直接返回，不执行删除
                }
                // 执行删除：拼接 ID 条件 + 自定义条件
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 删除数据列表
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                // 删除单条数据：解析 ID 并执行删除
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 发送数据变更通知（仅当有数据被删除时）
        if (count > 0) {
            // 若删除的是 DATA 表数据，额外通知便签 URI 刷新
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            // 通知当前 URI 对应的观察者
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    // ------------------------------ 核心方法：更新（update） ------------------------------
    /**
     * 对外提供数据更新接口，所有外部更新便签数据的请求都会走这个方法
     * @param uri 目标 URI（指定更新类型：便签列表/单条便签/数据等）
     * @param values 待更新的数据
     * @param selection 更新条件
     * @param selectionArgs 更新条件参数
     * @return 成功更新的行数
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0; // 存储更新的行数
        String id = null;
        // 获取可写数据库实例
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false; // 标记是否更新的是 DATA 表数据

        // 根据 URI 匹配码分发更新逻辑
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新便签列表：先递增便签版本号
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 更新单条便签：解析 ID，递增版本号后执行更新
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 更新数据列表
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                // 更新单条数据：解析 ID 并执行更新
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 发送数据变更通知（仅当有数据被更新时）
        if (count > 0) {
            // 若更新的是 DATA 表数据，额外通知便签 URI 刷新
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            // 通知当前 URI 对应的观察者
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    // ------------------------------ 私有工具方法 ------------------------------
    /**
     * 解析并拼接查询条件，避免 SQL 语法错误
     * @param selection 原始查询条件（可能为 null/空）
     * @return 拼接后的条件：非空则加 " AND (条件)"，空则返回空字符串
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 递增便签版本号（用于数据同步/版本校验）
     * @param id 便签 ID（-1 表示批量更新，非 -1 表示单条更新）
     * @param selection 批量更新条件
     * @param selectionArgs 条件参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        // 构建 UPDATE SQL 语句：VERSION = VERSION + 1
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        // 拼接 WHERE 子句（仅当有 ID 或自定义条件时）
        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        // 拼接 ID 条件（单条更新）
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        // 拼接自定义条件（批量更新）
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 替换条件中的 ? 占位符为实际参数
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        // 执行 SQL 语句，递增版本号
        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    // ------------------------------ 未实现方法 ------------------------------
    /**
     * 返回 URI 对应的 MIME 类型（当前版本未实现）
     * @param uri 目标 URI
     * @return null（暂未实现）
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

}