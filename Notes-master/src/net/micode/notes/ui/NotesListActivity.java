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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * 笔记列表主界面Activity
 * 负责笔记列表展示、文件夹管理、笔记增删改查、批量操作、同步等核心功能
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    // 异步查询Token - 文件夹下笔记列表查询
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;
    // 异步查询Token - 文件夹列表查询（用于笔记移动）
    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;

    // 文件夹上下文菜单ID - 删除
    private static final int MENU_FOLDER_DELETE = 0;
    // 文件夹上下文菜单ID - 查看
    private static final int MENU_FOLDER_VIEW = 1;
    // 文件夹上下文菜单ID - 重命名
    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    // SharedPreferences键 - 首次使用引导标识
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    // 列表编辑状态枚举
    private enum ListEditState {
        NOTE_LIST,      // 普通笔记列表状态
        SUB_FOLDER,     // 子文件夹状态
        CALL_RECORD_FOLDER // 通话记录文件夹状态
    };

    // 当前列表编辑状态
    private ListEditState mState;
    // 异步查询处理器（用于ContentProvider异步查询）
    private BackgroundQueryHandler mBackgroundQueryHandler;
    // 笔记列表适配器
    private NotesListAdapter mNotesListAdapter;
    // 笔记列表ListView
    private ListView mNotesListView;
    // 新建笔记按钮
    private Button mAddNewNote;
    // 触摸事件分发标识（用于新建按钮透明区域事件透传）
    private boolean mDispatch;
    // 触摸事件原始Y坐标
    private int mOriginY;
    // 触摸事件分发Y坐标
    private int mDispatchY;
    // 标题栏TextView（显示当前文件夹名称）
    private TextView mTitleBar;
    // 当前选中的文件夹ID
    private long mCurrentFolderId;
    // 内容解析器（用于操作ContentProvider）
    private ContentResolver mContentResolver;
    // 多选模式回调
    private ModeCallback mModeCallBack;
    // 日志TAG
    private static final String TAG = "NotesListActivity";
    // ListView滚动速率
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;
    // 当前聚焦的笔记数据项
    private NoteItemData mFocusNoteDataItem;

    // 普通文件夹查询条件（父文件夹ID匹配）
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";
    // 根文件夹查询条件（包含系统文件夹+通话记录文件夹）
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    // Activity跳转请求码 - 打开笔记
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    // Activity跳转请求码 - 新建笔记
    private final static int REQUEST_CODE_NEW_NODE  = 103;

    /**
     * Activity创建生命周期方法
     * 初始化布局、资源、首次使用引导
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        // 初始化资源
        initResources();

        /**
         * 首次使用应用时插入引导说明
         */
        setAppInfoFromRawRes();
    }

    /**
     * 处理子Activity返回结果
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 返回数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 打开/新建笔记返回成功时，清空列表适配器游标（触发重新查询）
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 从Raw资源文件读取首次使用引导内容并创建引导笔记
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        // 未添加过引导说明时执行
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                 // 读取raw目录下的introduction文件
                 in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                // 关闭输入流
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // 创建空笔记并写入引导内容
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            // 保存笔记并标记引导已添加
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    /**
     * Activity启动生命周期方法
     * 启动异步查询加载笔记列表
     */
    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }

    /**
     * 初始化界面资源和成员变量
     */
    private void initResources() {
        // 获取内容解析器
        mContentResolver = this.getContentResolver();
        // 初始化异步查询处理器
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        // 默认选中根文件夹
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        // 获取ListView控件
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        // 添加ListView底部视图
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        // 设置Item点击监听
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        // 设置Item长按监听
        mNotesListView.setOnItemLongClickListener(this);
        // 初始化列表适配器
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        // 获取新建笔记按钮并设置监听
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        // 初始化触摸事件变量
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        // 获取标题栏控件
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        // 初始化列表状态为普通笔记列表
        mState = ListEditState.NOTE_LIST;
        // 初始化多选模式回调
        mModeCallBack = new ModeCallback();
    }

    /**
     * 多选模式回调类
     * 处理ListView多选模式下的菜单创建、Item选中状态变更、菜单点击等逻辑
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        // 下拉菜单（全选/取消全选）
        private DropdownMenu mDropDownMenu;
        // 动作模式实例
        private ActionMode mActionMode;
        // 移动菜单Item
        private MenuItem mMoveMenu;

        /**
         * 创建动作模式时调用
         * @param mode 动作模式
         * @param menu 菜单
         * @return 是否创建成功
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // 加载多选菜单布局
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            // 设置删除菜单点击监听
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            // 获取移动菜单Item
            mMoveMenu = menu.findItem(R.id.move);
            
            // 通话记录文件夹或无用户文件夹时隐藏移动菜单
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            
            mActionMode = mode;
            // 设置适配器为多选模式
            mNotesListAdapter.setChoiceMode(true);
            // 禁用ListView长按（避免重复触发）
            mNotesListView.setLongClickable(false);
            // 隐藏新建笔记按钮
            mAddNewNote.setVisibility(View.GONE);

            // 加载自定义多选菜单视图（包含全选/取消全选）
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            // 初始化下拉菜单
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            // 设置下拉菜单点击监听（全选/取消全选）
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }
            });
            return true;
        }

        /**
         * 更新多选菜单状态（选中数量、全选按钮文本）
         */
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // 更新下拉菜单标题（显示选中数量）
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            // 更新全选/取消全选按钮状态
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        /**
         * 准备动作模式时调用
         * @param mode 动作模式
         * @param menu 菜单
         * @return 是否处理成功
         */
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * 动作模式菜单Item点击时调用
         * @param mode 动作模式
         * @param item 菜单Item
         * @return 是否处理成功
         */
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        /**
         * 销毁动作模式时调用
         * @param mode 动作模式
         */
        public void onDestroyActionMode(ActionMode mode) {
            // 退出多选模式
            mNotesListAdapter.setChoiceMode(false);
            // 启用ListView长按
            mNotesListView.setLongClickable(true);
            // 显示新建笔记按钮
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        /**
         * 结束动作模式
         */
        public void finishActionMode() {
            mActionMode.finish();
        }

        /**
         * Item选中状态变更时调用
         * @param mode 动作模式
         * @param position Item位置
         * @param id ItemID
         * @param checked 是否选中
         */
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            // 更新适配器中Item选中状态
            mNotesListAdapter.setCheckedItem(position, checked);
            // 更新菜单状态
            updateMenu();
        }

        /**
         * 菜单Item点击监听
         * @param item 菜单Item
         * @return 是否处理成功
         */
        public boolean onMenuItemClick(MenuItem item) {
            // 无选中项时提示
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            // 处理不同菜单点击事件
            switch (item.getItemId()) {
                case R.id.delete:
                    // 批量删除确认弹窗
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(getString(R.string.alert_title_delete));
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.setMessage(getString(R.string.alert_message_delete_notes,
                                             mNotesListAdapter.getSelectedCount()));
                    builder.setPositiveButton(android.R.string.ok,
                                             new DialogInterface.OnClickListener() {
                                                 public void onClick(DialogInterface dialog,
                                                         int which) {
                                                     // 执行批量删除
                                                     batchDelete();
                                                 }
                                             });
                    builder.setNegativeButton(android.R.string.cancel, null);
                    builder.show();
                    break;
                case R.id.move:
                    // 查询目标文件夹列表（用于移动笔记）
                    startQueryDestinationFolders();
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    /**
     * 新建笔记按钮触摸事件监听类
     * 处理按钮透明区域的触摸事件透传（透传给下方ListView）
     */
    private class NewNoteOnTouchListener implements OnTouchListener {

        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    // 获取屏幕和按钮尺寸
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) event.getY();
                    
                    // 子文件夹状态下减去标题栏高度
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }
                    
                    /**
                     * 特殊处理：将新建按钮透明区域的触摸事件透传给下方ListView
                     * 透明区域公式：y=-0.12x+94（像素），适配UI设计要求
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        // 获取ListView最后一个可见Item
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        // 校验Item位置并分发事件
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    // 移动事件透传
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
                default: {
                    // 其他事件（UP/CANCEL）透传
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            return false;
        }

    };

    /**
     * 启动异步查询加载当前文件夹下的笔记列表
     */
    private void startAsyncNotesListQuery() {
        // 根据当前文件夹是否为根文件夹选择不同查询条件
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        // 执行异步查询
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[] {
                    String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 异步查询处理器子类
     * 处理ContentProvider异步查询结果
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        /**
         * 查询完成回调
         * @param token 查询标识
         * @param cookie 附加数据
         * @param cursor 查询结果游标
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    // 更新笔记列表适配器游标
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    // 显示文件夹选择菜单
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
                    return;
            }
        }
    }

    /**
     * 显示文件夹选择菜单（用于笔记移动）
     * @param cursor 文件夹游标
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                // 执行批量移动笔记到选中文件夹
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                // 提示移动成功
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                // 结束多选模式
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    /**
     * 新建笔记
     * 跳转到笔记编辑页面
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * 批量删除笔记
     * 同步模式下移动到回收站，非同步模式下直接删除
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            /**
             * 后台执行删除逻辑
             * @param unused 无参数
             * @return 关联的Widget集合
             */
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                // 获取选中笔记关联的Widget
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // 非同步模式：直接删除
                    if (!DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // 同步模式：移动到回收站
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            /**
             * 主线程更新UI
             * @param widgets 关联的Widget集合
             */
            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                // 更新关联的Widget
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                // 结束多选模式
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    /**
     * 删除文件夹
     * @param folderId 文件夹ID
     */
    private void deleteFolder(long folderId) {
        // 根文件夹不允许删除
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        // 获取文件夹关联的Widget
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        
        if (!isSyncMode()) {
            // 非同步模式：直接删除
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // 同步模式：移动到回收站
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        
        // 更新关联的Widget
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    /**
     * 打开笔记编辑页面
     * @param data 笔记数据项
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 打开文件夹
     * @param data 文件夹数据项
     */
    private void openFolder(NoteItemData data) {
        // 更新当前文件夹ID
        mCurrentFolderId = data.getId();
        // 重新查询文件夹下的笔记
        startAsyncNotesListQuery();
        
        // 更新列表状态
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        
        // 更新标题栏
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    /**
     * 点击事件处理
     * @param v 点击的视图
     */
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_new_note:
                // 新建笔记
                createNewNote();
                break;
            default:
                break;
        }
    }

    /**
     * 显示软键盘
     */
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /**
     * 隐藏软键盘
     * @param view 关联视图
     */
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * 显示创建/修改文件夹对话框
     * @param create 是否为创建文件夹（false为修改）
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 加载对话框布局
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        // 显示软键盘
        showSoftInput();
        
        // 设置对话框标题和初始文本
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        // 设置按钮
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 取消时隐藏软键盘
                hideSoftInput(etName);
            }
        });

        // 显示对话框
        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        // 确定按钮点击监听
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                
                // 校验文件夹名称是否已存在
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                
                // 处理创建/修改逻辑
                if (!create) {
                    // 修改文件夹名称
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    // 创建新文件夹
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        // 初始状态下禁用确定按钮（无文本）
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        
        /**
         * 文本变化监听：为空时禁用确定按钮
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * 返回键处理
     * 子文件夹/通话记录文件夹状态下返回根文件夹，否则执行默认返回逻辑
     */
    @Override
    public void onBackPressed() {
        switch (mState) {
            case SUB_FOLDER:
                // 返回到根文件夹
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                // 返回到根文件夹并显示新建按钮
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
            default:
                break;
        }
    }

    /**
     * 更新桌面Widget
     * @param appWidgetId WidgetID
     * @param appWidgetType Widget类型（2x/4x）
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        // 根据类型选择对应的WidgetProvider
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        // 发送Widget更新广播
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            appWidgetId
        });
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 文件夹上下文菜单创建监听
     */
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    /**
     * 上下文菜单关闭时调用
     * @param menu 菜单
     */
    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            // 移除上下文菜单创建监听
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    /**
     * 上下文菜单Item选择监听
     * @param item 选中的Item
     * @return 是否处理成功
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        
        // 处理不同菜单Item
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                // 打开文件夹
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                // 删除文件夹确认弹窗
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_folder));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFolder(mFocusNoteDataItem.getId());
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                // 修改文件夹名称
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    /**
     * 准备选项菜单
     * 根据当前列表状态加载不同的菜单布局
     * @param menu 菜单
     * @return 是否加载成功
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            // 普通笔记列表状态 - 加载主菜单
            getMenuInflater().inflate(R.menu.note_list, menu);
            // 设置同步菜单文本（同步中/同步）
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            // 子文件夹状态 - 加载子文件夹菜单
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            // 通话记录文件夹状态 - 加载通话记录文件夹菜单
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    /**
     * 选项菜单Item选择监听
     * @param item 选中的Item
     * @return 是否处理成功
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_folder: {
                // 创建新文件夹
                showCreateOrModifyFolderDialog(true);
                break;
            }
            case R.id.menu_export_text: {
                // 导出笔记为文本
                exportNoteToText();
                break;
            }
            case R.id.menu_sync: {
                // 同步/取消同步
                if (isSyncMode()) {
                    if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                        GTaskSyncService.startSync(this);
                    } else {
                        GTaskSyncService.cancelSync(this);
                    }
                } else {
                    // 未配置同步账号时跳转到设置页面
                    startPreferenceActivity();
                }
                break;
            }
            case R.id.menu_setting: {
                // 跳转到设置页面
                startPreferenceActivity();
                break;
            }
            case R.id.menu_new_note: {
                // 新建笔记
                createNewNote();
                break;
            }
            case R.id.menu_search:
                // 触发搜索
                onSearchRequested();
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 触发搜索
     * @return 是否触发成功
     */
    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    /**
     * 导出笔记为文本文件到SD卡
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            /**
             * 后台执行导出逻辑
             * @param unused 无参数
             * @return 导出结果状态码
             */
            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            /**
             * 主线程处理导出结果
             * @param result 导出结果状态码
             */
            @Override
            protected void onPostExecute(Integer result) {
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                // 根据结果显示不同提示
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    builder.setTitle(NotesListActivity.this.getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(R.string.error_sdcard_unmounted));
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    builder.setTitle(NotesListActivity.this.getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    builder.setTitle(NotesListActivity.this.getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(R.string.error_sdcard_export));
                }
                builder.setPositiveButton(android.R.string.ok, null);
                builder.show();
            }

        }.execute();
    }

    /**
     * 判断是否为同步模式（已配置同步账号）
     * @return 是否为同步模式
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 启动设置页面
     */
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    /**
     * ListView Item点击监听类
     */
    private class OnListItemClickListener implements OnItemClickListener {

        /**
         * Item点击处理
         * @param parent 父容器
         * @param view 点击的视图
         * @param position 位置
         * @param id ItemID
         */
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                
                // 多选模式下处理Item选中状态
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                // 根据当前列表状态处理点击
                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            // 打开文件夹
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            // 打开笔记
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            // 打开笔记
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    /**
     * 启动查询目标文件夹列表（用于笔记移动）
     */
    private void startQueryDestinationFolders() {
        // 构建文件夹查询条件（排除回收站和当前文件夹）
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        // 非根文件夹状态下包含根文件夹
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
            "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        // 执行异步查询
        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * ListView Item长按监听
     * @param parent 父容器
     * @param view 长按的视图
     * @param position 位置
     * @param id ItemID
     * @return 是否处理成功
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            
            // 笔记Item长按 - 启动多选模式
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    // 触发长按震动反馈
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } 
            // 文件夹Item长按 - 注册上下文菜单
            else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}
