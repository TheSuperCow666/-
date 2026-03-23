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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟广播接收器
 * 作用：接收系统/应用发送的闹钟广播，触发闹钟提醒页面的展示
 * 继承自 BroadcastReceiver，是 Android 四大组件之一，用于接收广播事件
 */
public class AlarmReceiver extends BroadcastReceiver {
    /**
     * 广播接收回调方法：当接收到闹钟广播时执行此方法
     * @param context 上下文对象，用于获取应用环境信息、启动组件等
     * @param intent  意图对象，携带广播的相关数据和信息
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 重新设置 Intent 的目标组件为闹钟提醒页面（AlarmAlertActivity）
        intent.setClass(context, AlarmAlertActivity.class);
        // 添加 FLAG_ACTIVITY_NEW_TASK 标记：
        // 因为 BroadcastReceiver 不是 Activity 环境，启动 Activity 必须添加此标记，
        // 表示为新任务栈启动 Activity，否则会抛出异常
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 启动闹钟提醒页面
        context.startActivity(intent);
    }
}
