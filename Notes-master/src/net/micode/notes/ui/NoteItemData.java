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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记项数据模型类
 * 用于封装从数据库游标中读取的单条笔记/文件夹数据，包含笔记的基础属性、位置状态、通话记录关联信息等
 * 提供属性访问方法，方便UI层获取和展示笔记数据
 */
public class NoteItemData {
    // 数据库查询投影字段数组，定义需要从笔记表中查询的列
    static final String[] PROJECTION = new String[]{
            NoteColumns.ID,               // 笔记ID
            NoteColumns.ALERTED_DATE,     // 提醒时间
            NoteColumns.BG_COLOR_ID,      // 背景色ID
            NoteColumns.CREATED_DATE,     // 创建时间
            NoteColumns.HAS_ATTACHMENT,   // 是否有附件
            NoteColumns.MODIFIED_DATE,    // 修改时间
            NoteColumns.NOTES_COUNT,      // 子笔记数量（文件夹专用）
            NoteColumns.PARENT_ID,        // 父文件夹ID
            NoteColumns.SNIPPET,          // 笔记内容摘要
            NoteColumns.TYPE,             // 笔记类型（普通笔记/文件夹/系统文件夹）
            NoteColumns.WIDGET_ID,        // 关联的桌面组件ID
            NoteColumns.WIDGET_TYPE,      // 关联的桌面组件类型
    };

    // 投影字段对应的游标列索引常量，简化游标取值操作
    private static final int ID_COLUMN = 0;                    // 笔记ID列索引
    private static final int ALERTED_DATE_COLUMN = 1;          // 提醒时间列索引
    private static final int BG_COLOR_ID_COLUMN = 2;           // 背景色ID列索引
    private static final int CREATED_DATE_COLUMN = 3;          // 创建时间列索引
    private static final int HAS_ATTACHMENT_COLUMN = 4;        // 是否有附件列索引
    private static final int MODIFIED_DATE_COLUMN = 5;         // 修改时间列索引
    private static final int NOTES_COUNT_COLUMN = 6;           // 子笔记数量列索引
    private static final int PARENT_ID_COLUMN = 7;             // 父文件夹ID列索引
    private static final int SNIPPET_COLUMN = 8;               // 内容摘要列索引
    private static final int TYPE_COLUMN = 9;                  // 笔记类型列索引
    private static final int WIDGET_ID_COLUMN = 10;            // 桌面组件ID列索引
    private static final int WIDGET_TYPE_COLUMN = 11;          // 桌面组件类型列索引

    // 笔记核心属性
    private long mId;                 // 笔记唯一ID
    private long mAlertDate;          // 提醒时间（毫秒时间戳）
    private int mBgColorId;           // 背景色ID（对应预设的颜色值）
    private long mCreatedDate;        // 创建时间（毫秒时间戳）
    private boolean mHasAttachment;   // 是否包含附件（图片/音频等）
    private long mModifiedDate;       // 最后修改时间（毫秒时间戳）
    private int mNotesCount;          // 文件夹下的笔记数量
    private long mParentId;           // 父文件夹ID
    private String mSnippet;          // 笔记内容摘要（去除复选框标签）
    private int mType;                // 笔记类型：普通笔记/文件夹/系统文件夹
    private int mWidgetId;            // 关联的桌面组件ID
    private int mWidgetType;          // 关联的桌面组件类型

    // 通话记录专属属性
    private String mName;             // 通话联系人姓名（无则显示号码）
    private String mPhoneNumber;      // 通话记录对应的电话号码

    // 列表位置状态属性
    private boolean mIsLastItem;              // 是否为列表最后一项
    private boolean mIsFirstItem;             // 是否为列表第一项
    private boolean mIsOnlyOneItem;           // 是否为列表唯一一项
    private boolean mIsOneNoteFollowingFolder;// 是否是文件夹后仅有的一条笔记
    private boolean mIsMultiNotesFollowingFolder;// 是否是文件夹后多条笔记中的一条

    /**
     * 构造方法：从数据库游标初始化笔记项数据
     * @param context 上下文对象，用于访问ContentResolver、Contact等系统服务
     * @param cursor 数据库游标，已指向待读取的笔记记录行
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 从游标读取基础属性
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        // 转换是否有附件（数据库存储为int，0=无，1=有）
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        // 读取摘要并移除复选框标签（TAG_CHECKED/TAG_UNCHECKED）
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        // 初始化通话记录属性
        mPhoneNumber = "";
        // 如果当前笔记属于通话记录文件夹，读取关联的电话号码和联系人姓名
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            // 根据笔记ID获取通话号码
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                // 根据号码获取联系人姓名
                mName = Contact.getContact(context, mPhoneNumber);
                // 无联系人姓名则显示号码
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        // 兜底：姓名为空则置空字符串
        if (mName == null) {
            mName = "";
        }
        // 检查当前笔记在游标中的位置状态
        checkPostion(cursor);
    }

    /**
     * 检查并设置当前笔记在列表中的位置状态
     * 包括是否为首/尾/唯一项，以及是否为文件夹后的笔记
     * @param cursor 数据库游标，指向当前笔记记录
     */
    private void checkPostion(Cursor cursor) {
        // 设置基础位置状态
        mIsLastItem = cursor.isLast();
        mIsFirstItem = cursor.isFirst();
        mIsOnlyOneItem = (cursor.getCount() == 1);
        // 初始化文件夹后笔记状态为false
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 仅处理「普通笔记」且「非列表第一项」的情况
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            // 记录当前游标位置
            int position = cursor.getPosition();
            // 移动到上一条记录，判断是否为文件夹/系统文件夹
            if (cursor.moveToPrevious()) {
                int prevType = cursor.getInt(TYPE_COLUMN);
                if (prevType == Notes.TYPE_FOLDER || prevType == Notes.TYPE_SYSTEM) {
                    // 上一条是文件夹，判断当前笔记后是否还有更多笔记
                    if (cursor.getCount() > (position + 1)) {
                        // 有更多笔记 → 文件夹后多条笔记
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        // 无更多笔记 → 文件夹后仅一条笔记
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                // 恢复游标到原位置（必须保证能回退，否则抛异常）
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    /**
     * 是否是文件夹后仅有的一条笔记
     * @return true=是，false=否
     */
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    /**
     * 是否是文件夹后多条笔记中的一条
     * @return true=是，false=否
     */
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    /**
     * 是否为列表最后一项
     * @return true=是，false=否
     */
    public boolean isLast() {
        return mIsLastItem;
    }

    /**
     * 获取通话记录对应的联系人姓名（无则返回号码）
     * @return 姓名/电话号码
     */
    public String getCallName() {
        return mName;
    }

    /**
     * 是否为列表第一项
     * @return true=是，false=否
     */
    public boolean isFirst() {
        return mIsFirstItem;
    }

    /**
     * 是否为列表唯一一项
     * @return true=是，false=否
     */
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    /**
     * 获取笔记ID
     * @return 笔记唯一ID
     */
    public long getId() {
        return mId;
    }

    /**
     * 获取提醒时间
     * @return 提醒时间戳（毫秒），0=无提醒
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取创建时间
     * @return 创建时间戳（毫秒）
     */
    public long getCreatedDate() {
        return mCreatedDate;
    }

    /**
     * 是否包含附件
     * @return true=有附件，false=无附件
     */
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    /**
     * 获取最后修改时间
     * @return 修改时间戳（毫秒）
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取背景色ID
     * @return 背景色ID（对应预设的颜色值）
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取父文件夹ID
     * @return 父文件夹ID，0=无父文件夹
     */
    public long getParentId() {
        return mParentId;
    }

    /**
     * 获取文件夹下的笔记数量（仅文件夹类型有效）
     * @return 子笔记数量
     */
    public int getNotesCount() {
        return mNotesCount;
    }

    /**
     * 获取文件夹ID（兼容方法，同getParentId）
     * @return 父文件夹ID
     */
    public long getFolderId() {
        return mParentId;
    }

    /**
     * 获取笔记类型
     * @return 类型值：Notes.TYPE_NOTE/Notes.TYPE_FOLDER/Notes.TYPE_SYSTEM
     */
    public int getType() {
        return mType;
    }

    /**
     * 获取关联的桌面组件类型
     * @return 桌面组件类型值
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 获取关联的桌面组件ID
     * @return 桌面组件ID，0=无关联组件
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取笔记内容摘要（已去除复选框标签）
     * @return 内容摘要字符串
     */
    public String getSnippet() {
        return mSnippet;
    }

    /**
     * 是否设置了提醒
     * @return true=有提醒，false=无提醒
     */
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /**
     * 是否为通话记录笔记
     * @return true=是通话记录，false=普通笔记/文件夹
     */
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 静态工具方法：从游标中读取笔记类型
     * @param cursor 数据库游标（已定位到目标行）
     * @return 笔记类型值
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}
