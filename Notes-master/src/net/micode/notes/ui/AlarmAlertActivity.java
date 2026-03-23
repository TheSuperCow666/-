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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 闹钟提醒Activity：用于展示笔记的闹钟提醒弹窗、播放提醒铃声，
 * 并处理用户点击“确定”/“进入笔记”的交互逻辑
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    // 笔记ID，用于关联当前提醒的笔记
    private long mNoteId;
    // 笔记内容摘要（用于弹窗展示）
    private String mSnippet;
    // 摘要展示的最大长度限制
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    // 媒体播放器：用于播放闹钟提醒铃声
    MediaPlayer mPlayer;

    /**
     * 生命周期方法：Activity创建时执行
     * 初始化窗口属性、获取笔记信息、校验笔记有效性、展示弹窗、播放铃声
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 添加窗口标记：锁屏状态下显示窗口
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕处于熄灭状态，添加额外窗口标记
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON // 保持屏幕常亮
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   // 打开屏幕
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON // 屏幕亮时允许锁屏
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR); // 布局适配装饰栏
        }

        // 获取启动当前Activity的Intent（携带笔记ID信息）
        Intent intent = getIntent();

        try {
            // 从Intent的Data中解析笔记ID（路径分段的第二个参数）
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            // 根据笔记ID获取笔记内容摘要
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            // 摘要长度超过限制时，截取并拼接省略符
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? 
                mSnippet.substring(0, SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                : mSnippet;
        } catch (IllegalArgumentException e) {
            // 解析笔记ID失败时打印异常，结束Activity
            e.printStackTrace();
            return;
        }

        // 初始化媒体播放器
        mPlayer = new MediaPlayer();
        // 校验笔记是否存在且为普通笔记类型，有效则展示弹窗并播放铃声
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog(); // 展示提醒弹窗
            playAlarmSound();   // 播放提醒铃声
        } else {
            // 笔记无效时直接结束Activity
            finish();
        }
    }

    /**
     * 判断屏幕是否处于点亮状态
     * @return true-屏幕亮；false-屏幕熄灭
     */
    private boolean isScreenOn() {
        // 获取电源管理服务
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        // 返回屏幕状态
        return pm.isScreenOn();
    }

    /**
     * 播放闹钟提醒铃声
     * 1. 获取系统默认闹钟铃声Uri
     * 2. 设置音频流类型（适配静音模式）
     * 3. 初始化播放器并循环播放铃声
     */
    private void playAlarmSound() {
        // 获取系统默认的闹钟铃声Uri
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取系统静音模式下受影响的音频流配置
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 根据静音模式配置设置音频流类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            // 默认使用闹钟音频流
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }

        try {
            // 设置播放器数据源（系统闹钟铃声）
            mPlayer.setDataSource(this, url);
            // 准备播放器
            mPlayer.prepare();
            // 设置循环播放（持续响铃直到用户操作）
            mPlayer.setLooping(true);
            // 开始播放铃声
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 展示提醒操作弹窗
     * 弹窗包含笔记摘要、“确定”按钮；屏幕亮时额外显示“进入笔记”按钮
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name); // 设置弹窗标题（应用名称）
        dialog.setMessage(mSnippet);       // 设置弹窗内容（笔记摘要）
        // 设置“确定”按钮，点击事件由当前Activity处理
        dialog.setPositiveButton(R.string.notealert_ok, this);
        // 屏幕亮时显示“进入笔记”按钮
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        // 展示弹窗，并设置弹窗消失的监听
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 弹窗按钮点击事件处理
     * @param dialog 弹窗实例
     * @param which  点击的按钮类型（BUTTON_POSITIVE/BUTTON_NEGATIVE）
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // 点击“进入笔记”按钮：跳转到笔记编辑页面查看该笔记
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW); // 设置动作：查看笔记
                intent.putExtra(Intent.EXTRA_UID, mNoteId); // 携带笔记ID参数
                startActivity(intent);
                break;
            default:
                // 点击“确定”按钮：无额外操作（弹窗消失后会停止铃声并结束Activity）
                break;
        }
    }

    /**
     * 弹窗消失监听：弹窗关闭时停止铃声并结束Activity
     * @param dialog 弹窗实例
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound(); // 停止播放铃声
        finish();         // 结束当前Activity
    }

    /**
     * 停止闹钟铃声播放
     * 释放MediaPlayer资源，避免内存泄漏
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();    // 停止播放
            mPlayer.release(); // 释放播放器资源
            mPlayer = null;    // 置空引用
        }
    }
}
