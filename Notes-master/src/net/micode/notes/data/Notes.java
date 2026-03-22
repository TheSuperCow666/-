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

import android.net.Uri;

/**
 * 小米便签核心常量定义类
 * 作用：统一管理整个便签应用的常量（URI、数据类型、Intent参数、数据库列名等），避免硬编码
 * 特性：纯常量类，无业务逻辑，所有成员均为static final，供全应用调用
 */
public class Notes {
    // ContentProvider的权限标识（唯一标识符），用于URI匹配和跨应用数据访问授权
    public static final String AUTHORITY = "micode_notes";
    // 日志标签：用于Logcat调试时定位本类相关日志
    public static final String TAG = "Notes";

    // 数据类型常量：区分便签/文件夹/系统文件夹
    public static final int TYPE_NOTE     = 0;    // 普通便签
    public static final int TYPE_FOLDER   = 1;    // 用户自定义文件夹
    public static final int TYPE_SYSTEM   = 2;    // 系统级文件夹（如回收站、通话记录文件夹）

    /**
     * 系统文件夹ID常量（负数标识，区分用户自定义文件夹）
     * {@link Notes#ID_ROOT_FOLDER } ：默认根文件夹（所有普通便签默认归属）
     * {@link Notes#ID_TEMPARAY_FOLDER } ：临时文件夹（无归属的便签）
     * {@link Notes#ID_CALL_RECORD_FOLDER} ：通话记录便签专属文件夹
     * {@link Notes#ID_TRASH_FOLER} ：回收站文件夹（删除的便签暂存）
     */
    public static final int ID_ROOT_FOLDER = 0;               // 根文件夹（默认）
    public static final int ID_TEMPARAY_FOLDER = -1;          // 临时文件夹
    public static final int ID_CALL_RECORD_FOLDER = -2;       // 通话记录文件夹
    public static final int ID_TRASH_FOLER = -3;              // 回收站文件夹（注意：原代码拼写错误FOLER，应为FOLDER）

    // Intent跳转参数常量：用于组件间传递数据（如Activity/Service/Widget）
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";         // 提醒时间
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id"; // 背景色ID
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";           // 桌面小部件ID
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";       // 桌面小部件类型
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";           // 文件夹ID
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";           // 通话记录时间

    // 桌面小部件类型常量
    public static final int TYPE_WIDGET_INVALIDE      = -1;   // 无效/未初始化的小部件
    public static final int TYPE_WIDGET_2X            = 0;    // 2x尺寸小部件（如2x1、2x2）
    public static final int TYPE_WIDGET_4X            = 1;    // 4x尺寸小部件（如4x1、4x2）

    /**
     * 数据类型常量内部类：统一管理便签类型标识
     * 作用：对外暴露标准化的便签类型字符串，避免直接使用硬编码字符串
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;       // 文本便签类型标识
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE; // 通话记录便签类型标识
    }

    /**
     * 核心URI常量：供ContentResolver访问数据
     * CONTENT_NOTE_URI：查询所有便签和文件夹的URI
     * CONTENT_DATA_URI：查询便签详情数据的URI
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note"); // 便签/文件夹主表URI
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data"); // 便签详情数据表URI

    /**
     * Note表（便签/文件夹主表）列名接口
     * 作用：定义Note表所有字段的名称和数据类型，与数据库表结构一一对应
     */
    public interface NoteColumns {
        /**
         * 行唯一标识ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * 父文件夹ID（用于关联文件夹）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 创建时间（时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间（时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 提醒时间（时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 文件夹名称 / 便签文本内容摘要
         * <P> 数据类型: TEXT </P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * 便签关联的桌面小部件ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 便签关联的桌面小部件类型（对应TYPE_WIDGET_2X/TYPE_WIDGET_4X）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 便签背景色ID（对应预设的背景色配置）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 是否有附件标识（多媒体便签专用）
         * <P> 数据类型: INTEGER </P>
         * 取值：0=无附件（纯文本），1=有附件（图片/音频等）
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 文件夹下的便签数量（仅文件夹行有效）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 数据类型（对应TYPE_NOTE/TYPE_FOLDER/TYPE_SYSTEM）
         * <P> 数据类型: INTEGER </P>
         */
        public static final String TYPE = "type";

        /**
         * 最后同步ID（云同步专用）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 本地修改标识（云同步专用）
         * <P> 数据类型: INTEGER </P>
         * 取值：0=未修改，1=本地已修改待同步
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移入临时文件夹前的原始父文件夹ID（用于恢复便签归属）
         * <P> 数据类型 : INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * Google Task ID（谷歌任务同步专用）
         * <P> 数据类型 : TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 版本号（用于数据兼容/升级）
         * <P> 数据类型 : INTEGER (long) </P>
         */
        public static final String VERSION = "version";
    }

    /**
     * Data表（便签详情表）列名接口
     * 作用：定义Data表所有字段的名称和数据类型，存储便签的具体内容（文本/通话记录等）
     */
    public interface DataColumns {
        /**
         * 行唯一标识ID
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * MIME类型（区分不同类型的便签内容：文本/通话记录/多媒体）
         * <P> 数据类型: Text </P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 关联的Note表ID（用于关联主表）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 创建时间（时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间（时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 数据内容（通用字段，存储便签核心内容）
         * <P> 数据类型: TEXT </P>
         */
        public static final String CONTENT = "content";

        /**
         * 通用整型数据列1（MIME类型专属，如文本便签的模式、通话记录的时间）
         * <P> 数据类型: INTEGER </P>
         */
        public static final String DATA1 = "data1";

        /**
         * 通用整型数据列2（MIME类型专属）
         * <P> 数据类型: INTEGER </P>
         */
        public static final String DATA2 = "data2";

        /**
         * 通用文本数据列3（MIME类型专属，如通话记录的电话号码）
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA3 = "data3";

        /**
         * 通用文本数据列4（MIME类型专属）
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * 通用文本数据列5（MIME类型专属）
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本便签专属常量类
     * 实现DataColumns接口，复用通用列名，补充文本便签的专属常量
     */
    public static final class TextNote implements DataColumns {
        /**
         * 文本便签模式标识（是否为清单模式）
         * <P> 数据类型: Integer </P>
         * 取值：1=清单模式（带复选框），0=普通文本模式
         */
        public static final String MODE = DATA1;
        public static final int MODE_CHECK_LIST = 1; // 清单模式标识值

        // MIME类型常量：供ContentProvider识别文本便签
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";       // 文本便签列表MIME类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note"; // 单条文本便签MIME类型

        // 文本便签专属URI：用于访问文本便签数据
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录便签专属常量类
     * 实现DataColumns接口，复用通用列名，补充通话记录便签的专属常量
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话时间（时间戳）
         * <P> 数据类型: INTEGER (long) </P>
         * 映射到通用列DATA1
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码
         * <P> 数据类型: TEXT </P>
         * 映射到通用列DATA3
         */
        public static final String PHONE_NUMBER = DATA3;

        // MIME类型常量：供ContentProvider识别通话记录便签
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";       // 通话记录便签列表MIME类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note"; // 单条通话记录便签MIME类型

        // 通话记录便签专属URI：用于访问通话记录便签数据
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}