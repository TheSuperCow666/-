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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 笔记列表项视图类，继承自LinearLayout，用于展示单个笔记/文件夹/通话记录项的UI
 * 负责绑定数据、更新UI元素（标题、时间、提醒图标、背景等）、处理选择模式显示
 */
public class NotesListItem extends LinearLayout {
    // 提醒图标（闹钟/通话记录图标）
    private ImageView mAlert;
    // 笔记/文件夹标题文本
    private TextView mTitle;
    // 最后修改时间文本
    private TextView mTime;
    // 通话记录名称文本（仅通话记录项显示）
    private TextView mCallName;
    // 当前列表项绑定的数据模型
    private NoteItemData mItemData;
    // 选择模式下的复选框
    private CheckBox mCheckBox;

    /**
     * 构造方法，初始化视图控件
     * @param context 上下文对象，用于加载布局和控件
     */
    public NotesListItem(Context context) {
        super(context);
        // 加载列表项布局文件
        inflate(context, R.layout.note_item, this);
        // 初始化控件引用
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定数据到列表项视图，根据数据类型和状态更新UI
     * @param context  上下文对象，用于资源获取和样式设置
     * @param data     列表项数据模型（笔记/文件夹/通话记录）
     * @param choiceMode 是否开启选择模式（决定复选框是否显示）
     * @param checked  选择模式下当前项是否被选中
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // 选择模式且为普通笔记类型时，显示复选框并设置选中状态；否则隐藏复选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        // 保存当前项数据引用
        mItemData = data;

        // ********** 分类型处理UI展示 **********
        // 1. 通话记录文件夹项
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);          // 隐藏通话名称
            mAlert.setVisibility(View.VISIBLE);          // 显示提醒图标（通话记录图标）
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem); // 设置标题样式
            // 标题显示：通话记录文件夹名 + 文件夹内文件数量
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record); // 设置通话记录图标

        // 2. 通话记录子项（属于通话记录文件夹的子项）
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);       // 显示通话名称
            mCallName.setText(data.getCallName());       // 设置通话名称文本
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem); // 设置标题次要样式
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet())); // 设置格式化后的笔记摘要
            // 有提醒则显示闹钟图标，否则隐藏
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }

        // 3. 普通笔记/普通文件夹项
        } else {
            mCallName.setVisibility(View.GONE);          // 隐藏通话名称
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem); // 设置标题主要样式

            // 3.1 普通文件夹类型
            if (data.getType() == Notes.TYPE_FOLDER) {
                // 标题显示：文件夹名 + 文件夹内文件数量
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                                data.getNotesCount()));
                mAlert.setVisibility(View.GONE);         // 隐藏提醒图标

            // 3.2 普通笔记类型
            } else {
                // 标题显示格式化后的笔记摘要
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                // 有提醒则显示闹钟图标，否则隐藏
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // 设置最后修改时间（相对时间格式，如“1分钟前”）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // 设置列表项背景样式
        setBackground(data);
    }

    /**
     * 根据数据类型和状态设置列表项背景资源
     * @param data 列表项数据模型，包含背景色ID、类型、位置状态（首项/末项/单独项等）
     */
    private void setBackground(NoteItemData data) {
        // 获取背景色ID
        int id = data.getBgColorId();

        // 普通笔记类型：根据位置状态（首/末/单独/普通）设置不同背景
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                // 单独项 / 文件夹下唯一子项：设置单独项背景
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                // 列表末项：设置末项背景
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                // 列表首项 / 文件夹下多个子项的首项：设置首项背景
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                // 普通中间项：设置普通背景
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹类型：设置文件夹统一背景
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项绑定的数据模型
     * @return NoteItemData 当前项的数据模型
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}
