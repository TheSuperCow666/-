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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/**
 * 笔记应用的设置页面Activity
 * 主要功能：管理Google账号同步、显示同步状态、触发手动同步、处理账号切换/移除等逻辑
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    // 共享偏好设置文件名
    public static final String PREFERENCE_NAME = "notes_preferences";
    // 同步账号名称的偏好键
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";
    // 最后一次同步时间的偏好键
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";
    // 背景颜色随机显示的偏好键（当前类未使用，预留/对外暴露）
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    // 同步账号分类的偏好键（内部使用）
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";
    // 账号筛选的权限关键字（Google账号）
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    // 账号设置的偏好分类容器
    private PreferenceCategory mAccountCategory;
    // 接收同步服务广播的接收器
    private GTaskReceiver mReceiver;
    // 记录原始的Google账号列表（用于检测账号新增）
    private Account[] mOriAccounts;
    // 标记是否触发了添加账号的操作
    private boolean mHasAddedAccount;

    /**
     * 初始化Activity
     * @param icicle 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 启用ActionBar的返回按钮
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 加载偏好设置布局（res/xml/preferences.xml）
        addPreferencesFromResource(R.xml.preferences);
        // 获取账号设置的偏好分类容器
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        
        // 初始化并注册同步服务广播接收器
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        // 初始化原始账号列表为null
        mOriAccounts = null;
        // 给设置页面添加头部布局（res/layout/settings_header.xml）
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    /**
     * 恢复Activity（前台可见）
     * 核心逻辑：检测是否新增了Google账号，自动绑定同步账号；刷新UI
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 如果触发了添加账号操作，检测是否有新账号新增
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            // 对比原始账号列表，若新账号数量更多，说明新增了账号
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                for (Account accountNew : accounts) {
                    boolean found = false;
                    // 遍历原始账号，确认新账号是否为新增
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    // 找到新增账号，设置为同步账号并终止遍历
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        // 刷新页面UI（账号设置、同步按钮状态、最后同步时间）
        refreshUI();
    }

    /**
     * 销毁Activity
     * 核心逻辑：注销广播接收器，防止内存泄漏
     */
    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * 加载账号偏好设置项
     * 核心逻辑：构建账号选择的偏好项，绑定点击事件（选择/切换账号）
     */
    private void loadAccountPreference() {
        // 清空原有账号偏好项（避免重复添加）
        mAccountCategory.removeAll();

        // 创建账号设置的偏好项
        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        // 设置偏好项标题和摘要（从字符串资源读取）
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
        
        // 绑定偏好项点击事件
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                // 同步中不允许切换账号
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // 未设置过账号：显示账号选择弹窗
                        showSelectAccountAlertDialog();
                    } else {
                        // 已设置账号：显示切换账号的确认弹窗（提示风险）
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    // 同步中提示无法切换账号
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        // 将账号偏好项添加到分类容器中
        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 加载同步按钮和最后同步时间
     * 核心逻辑：根据同步状态更新按钮文字/点击事件；显示最后同步时间
     */
    private void loadSyncButton() {
        // 获取同步按钮和最后同步时间的TextView
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // 根据同步状态设置按钮状态
        if (GTaskSyncService.isSyncing()) {
            // 同步中：按钮显示“取消同步”，点击取消同步
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            // 未同步：按钮显示“立即同步”，点击触发同步
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        // 未设置同步账号时，禁用同步按钮
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // 设置最后同步时间显示
        if (GTaskSyncService.isSyncing()) {
            // 同步中：显示同步进度文本
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            // 未同步：读取最后同步时间，有值则显示，无值则隐藏
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 刷新整个页面UI
     * 核心逻辑：重新加载账号偏好项和同步按钮状态
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    /**
     * 显示账号选择弹窗（首次设置账号时调用）
     * 核心逻辑：列出所有Google账号供选择；提供“添加账号”入口
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 加载弹窗标题布局（自定义标题+提示）
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null); // 隐藏默认确认按钮

        // 获取所有Google账号
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        // 记录当前账号列表（用于后续检测新增）
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            // 有Google账号：构建账号选择列表
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            // 遍历账号，初始化选择列表，标记已选中的账号
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            // 设置单选列表，选择后绑定同步账号并刷新UI
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        // 添加“添加账号”的视图入口
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        // 显示弹窗，绑定“添加账号”点击事件（跳转到系统添加账号页面）
        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true; // 标记触发了添加账号操作
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls" // 筛选Google账号
                });
                startActivityForResult(intent, -1); // 无返回值，仅触发页面跳转
                dialog.dismiss();
            }
        });
    }

    /**
     * 显示切换账号的确认弹窗（已设置账号时调用）
     * 核心逻辑：提示切换账号的风险，提供“切换账号”/“移除账号”/“取消”选项
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 加载自定义标题布局（提示切换账号的风险）
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        // 构建弹窗选项：切换账号、移除账号、取消
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        // 绑定选项点击事件
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // 切换账号：显示账号选择弹窗
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    // 移除账号：清空同步账号配置
                    removeSyncAccount();
                    refreshUI();
                }
                // which==2：取消，无需处理
            }
        });
        dialogBuilder.show();
    }

    /**
     * 获取设备上所有的Google账号
     * @return Google账号数组
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        // 根据账号类型筛选（com.google为Google账号）
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 设置同步账号
     * @param account 选中的账号名称
     */
    private void setSyncAccount(String account) {
        // 账号未变化时，不执行操作
        if (getSyncAccountName(this).equals(account)) {
            return;
        }

        // 保存账号到共享偏好设置
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (account != null) {
            editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
        } else {
            editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
        }
        editor.commit();

        // 清空最后同步时间（切换账号后，同步时间重置）
        setLastSyncTime(this, 0);

        // 清空本地与GTask相关的同步数据（异步执行，避免阻塞UI）
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, ""); // 清空GTask ID
                values.put(NoteColumns.SYNC_ID, 0);  // 清空同步ID
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();

        // 提示用户账号设置成功
        Toast.makeText(NotesPreferenceActivity.this,
                getString(R.string.preferences_toast_success_set_accout, account),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * 移除同步账号
     * 核心逻辑：清空账号配置、最后同步时间；清除本地GTask同步数据
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        // 移除账号配置
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        // 移除最后同步时间
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // 清空本地与GTask相关的同步数据（异步执行）
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    /**
     * 获取当前设置的同步账号名称（静态方法，供外部调用）
     * @param context 上下文
     * @return 同步账号名称（空字符串表示未设置）
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 设置最后同步时间（静态方法，供外部调用）
     * @param context 上下文
     * @param time 同步时间戳（毫秒）
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 获取最后同步时间（静态方法，供外部调用）
     * @param context 上下文
     * @return 最后同步时间戳（0表示从未同步）
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /**
     * 同步服务广播接收器
     * 监听同步服务的广播，更新同步状态UI
     */
    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // 刷新整个UI（同步按钮状态、最后同步时间）
            refreshUI();
            // 如果正在同步，更新同步进度文本
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }
        }
    }

    /**
     * 处理ActionBar菜单点击事件
     * @param item 选中的菜单项
     * @return 事件是否处理成功
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // 点击返回按钮：跳转到笔记列表页，清除栈顶
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}
