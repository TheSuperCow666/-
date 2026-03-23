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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * 笔记列表适配器，继承自CursorAdapter，用于将笔记数据（Cursor）绑定到列表视图
 * 核心功能：管理笔记列表的显示、多选模式、选中状态、数据统计等
 */
public class NotesListAdapter extends CursorAdapter {
    // 日志标签，用于调试和错误输出
    private static final String TAG = "NotesListAdapter";
    // 上下文对象，用于访问应用资源和服务
    private Context mContext;
    // 存储选中项的位置和选中状态（key: 列表位置，value: 是否选中）
    private HashMap<Integer, Boolean> mSelectedIndex;
    // 笔记总数（过滤掉非笔记类型的条目）
    private int mNotesCount;
    // 是否开启多选模式
    private boolean mChoiceMode;

    /**
     * 桌面小部件属性封装类
     * 用于存储选中的小部件ID和类型
     */
    public static class AppWidgetAttribute {
        // 小部件唯一标识ID
        public int widgetId;
        // 小部件类型（区分不同样式/功能的小部件）
        public int widgetType;
    };

    /**
     * 构造方法，初始化适配器
     * @param context 上下文对象
     */
    public NotesListAdapter(Context context) {
        super(context, null); // 初始化父类，初始Cursor为null
        mSelectedIndex = new HashMap<Integer, Boolean>(); // 初始化选中状态集合
        mContext = context; // 保存上下文引用
        mNotesCount = 0; // 初始化笔记数量
    }

    /**
     * 创建新的列表项视图（ListView复用机制）
     * @param context 上下文对象
     * @param cursor 数据游标（当前位置的数据源）
     * @param parent 父容器（ListView）
     * @return 新创建的笔记列表项视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context); // 创建自定义笔记列表项
    }

    /**
     * 绑定数据到列表项视图（ListView复用机制）
     * @param view 要绑定的视图（已复用或新创建）
     * @param context 上下文对象
     * @param cursor 数据游标（包含当前项的笔记数据）
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // 检查视图类型是否为自定义笔记列表项
        if (view instanceof NotesListItem) {
            // 从游标中解析笔记数据
            NoteItemData itemData = new NoteItemData(context, cursor);
            // 绑定数据到视图，传入多选模式和当前项选中状态
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置指定位置项的选中状态
     * @param position 列表项位置
     * @param checked 是否选中
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked); // 更新选中状态集合
        notifyDataSetChanged(); // 通知列表数据变化，刷新视图
    }

    /**
     * 判断是否处于多选模式
     * @return true: 多选模式开启，false: 普通模式
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置多选模式状态
     * @param mode true: 开启多选，false: 关闭多选（同时清空选中状态）
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear(); // 清空选中状态
        mChoiceMode = mode; // 更新多选模式标识
    }

    /**
     * 全选/取消全选笔记（仅针对TYPE_NOTE类型的条目）
     * @param checked true: 全选，false: 取消全选
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor(); // 获取当前数据游标
        // 遍历所有列表项
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) { // 移动游标到指定位置
                // 过滤出笔记类型的条目（排除文件夹、小部件等类型）
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked); // 设置选中状态
                }
            }
        }
    }

    /**
     * 获取所有选中项的笔记ID集合
     * @return 选中的笔记ID集合（排除根文件夹ID）
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>(); // 初始化ID集合
        // 遍历所有选中的位置
        for (Integer position : mSelectedIndex.keySet()) {
            // 仅处理选中状态为true的项
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position); // 获取当前位置的笔记ID
                // 过滤根文件夹ID（无效笔记ID）
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen"); // 输出调试日志
                } else {
                    itemSet.add(id); // 添加有效ID到集合
                }
            }
        }
        return itemSet; // 返回选中的ID集合
    }

    /**
     * 获取所有选中的桌面小部件属性集合
     * @return 选中的小部件属性集合（无效游标时返回null）
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>(); // 初始化小部件集合
        // 遍历所有选中的位置
        for (Integer position : mSelectedIndex.keySet()) {
            // 仅处理选中状态为true的项
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position); // 获取当前位置的游标
                if (c != null) { // 检查游标有效性
                    AppWidgetAttribute widget = new AppWidgetAttribute(); // 创建小部件属性对象
                    NoteItemData item = new NoteItemData(mContext, c); // 解析游标数据
                    widget.widgetId = item.getWidgetId(); // 获取小部件ID
                    widget.widgetType = item.getWidgetType(); // 获取小部件类型
                    itemSet.add(widget); // 添加到集合
                    /**
                     * 注意：此处不要关闭游标，仅适配器有权限管理游标生命周期
                     */
                } else {
                    Log.e(TAG, "Invalid cursor"); // 输出错误日志
                    return null; // 游标无效时返回null
                }
            }
        }
        return itemSet; // 返回选中的小部件集合
    }

    /**
     * 获取选中项的数量
     * @return 选中的条目数
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values(); // 获取所有选中状态值
        if (null == values) { // 空值检查
            return 0;
        }
        Iterator<Boolean> iter = values.iterator(); // 迭代器遍历
        int count = 0; // 初始化选中数量
        while (iter.hasNext()) {
            if (true == iter.next()) { // 统计选中状态为true的项
                count++;
            }
        }
        return count; // 返回选中总数
    }

    /**
     * 判断是否所有笔记都被选中
     * @return true: 全选，false: 未全选（或无选中项）
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount(); // 获取选中数量
        // 选中数非0且等于笔记总数时，判定为全选
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    /**
     * 判断指定位置的项是否被选中
     * @param position 列表项位置
     * @return true: 选中，false: 未选中
     */
    public boolean isSelectedItem(final int position) {
        // 未存储该位置状态时，默认未选中
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position); // 返回存储的选中状态
    }

    /**
     * 内容变化时的回调（如数据库数据更新）
     * 重写以重新计算笔记总数
     */
    @Override
    protected void onContentChanged() {
        super.onContentChanged(); // 调用父类方法
        calcNotesCount(); // 重新计算笔记数量
    }

    /**
     * 更换游标时的回调
     * 重写以重新计算笔记总数
     * @param cursor 新的游标对象
     */
    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor); // 调用父类方法更换游标
        calcNotesCount(); // 重新计算笔记数量
    }

    /**
     * 计算有效笔记数量（仅统计TYPE_NOTE类型的条目）
     * 遍历游标，过滤非笔记类型，更新mNotesCount
     */
    private void calcNotesCount() {
        mNotesCount = 0; // 重置计数
        // 遍历所有列表项
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i); // 获取当前位置的游标
            if (c != null) { // 检查游标有效性
                // 过滤出笔记类型的条目
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++; // 统计有效笔记数
                }
            } else {
                Log.e(TAG, "Invalid cursor"); // 输出错误日志
                return; // 游标无效时终止计算
            }
        }
    }
}
