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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 * 继承自CursorAdapter，用于将数据库中的文件夹数据适配到列表视图中
 * 核心功能：绑定Cursor数据到列表项View，展示文件夹名称
 */
public class FoldersListAdapter extends CursorAdapter {
    /**
     * 查询数据库时的字段投影
     * 仅查询需要的字段，减少数据加载量
     */
    public static final String[] PROJECTION = {
        NoteColumns.ID,      // 文件夹ID字段
        NoteColumns.SNIPPET  // 文件夹名称/摘要字段
    };

    /**
     * PROJECTION数组对应的列索引常量
     * 便于代码中快速引用对应列，避免硬编码数字
     */
    public static final int ID_COLUMN = 0;     // 文件夹ID列索引
    public static final int NAME_COLUMN = 1;   // 文件夹名称列索引

    /**
     * 构造方法
     * @param context 上下文对象，用于资源获取、View创建等
     * @param c 数据库游标，封装了文件夹数据的查询结果
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    /**
     * 创建新的列表项View
     * CursorAdapter抽象方法，当需要新View时调用
     * @param context 上下文对象
     * @param cursor 当前位置的游标（未移动，仅用于获取数据元信息）
     * @param parent 父容器（列表视图）
     * @return 新建的文件夹列表项View
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 绑定游标数据到列表项View
     * CursorAdapter抽象方法，当View需要显示数据时调用
     * @param view 要绑定数据的列表项View
     * @param context 上下文对象
     * @param cursor 当前位置的游标（已移动到对应行）
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // 校验View类型，确保是自定义的FolderListItem
        if (view instanceof FolderListItem) {
            // 获取文件夹名称：如果是根文件夹，显示预设的父文件夹名称；否则显示游标中的名称
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) 
                    ? context.getString(R.string.menu_move_parent_folder)  // 根文件夹名称（资源文件中定义）
                    : cursor.getString(NAME_COLUMN);                       // 普通文件夹名称（数据库中读取）
            
            // 将名称绑定到View上
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 获取指定位置的文件夹名称
     * 对外提供的便捷方法，用于快速获取某位置的文件夹名称
     * @param context 上下文对象，用于根文件夹名称的资源获取
     * @param position 列表项位置
     * @return 对应位置的文件夹名称
     */
    public String getFolderName(Context context, int position) {
        // 获取对应位置的游标对象
        Cursor cursor = (Cursor) getItem(position);
        // 逻辑同bindView：根文件夹显示预设名称，普通文件夹显示数据库名称
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) 
                ? context.getString(R.string.menu_move_parent_folder) 
                : cursor.getString(NAME_COLUMN);
    }

    /**
     * 文件夹列表项View
     * 自定义LinearLayout，封装列表项的布局和UI元素
     * 职责：初始化布局、提供数据绑定方法
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;  // 显示文件夹名称的TextView

        /**
         * 构造方法
         * @param context 上下文对象
         */
        public FolderListItem(Context context) {
            super(context);
            // 加载列表项布局文件（folder_list_item.xml）
            inflate(context, R.layout.folder_list_item, this);
            // 初始化TextView控件
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 数据绑定方法
         * 将文件夹名称设置到TextView上
         * @param name 文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
