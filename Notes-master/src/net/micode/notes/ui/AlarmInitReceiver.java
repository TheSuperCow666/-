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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 * 作用：接收广播后，查询数据库中所有未触发的笔记闹钟，重新注册到系统闹钟管理器中
 * 场景：通常在应用启动、设备重启等场景下触发，保证未触发的闹钟不会丢失
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 查询数据库的字段投影：仅查询需要的字段，减少性能开销
    private static final String[] PROJECTION = new String[]{
        NoteColumns.ID,          // 笔记ID
        NoteColumns.ALERTED_DATE // 闹钟触发时间（毫秒级时间戳）
    };

    // 投影字段对应的索引，方便Cursor取值
    private static final int COLUMN_ID = 0;                // 笔记ID索引
    private static final int COLUMN_ALERTED_DATE = 1;      // 闹钟时间索引

    /**
     * 广播接收核心方法：接收到广播时执行
     * @param context 上下文对象，用于获取系统服务、内容解析器等
     * @param intent  触发广播的意图对象，可携带额外参数
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间（毫秒级时间戳），用于筛选未触发的闹钟
        long currentDate = System.currentTimeMillis();

        // 1. 查询数据库：获取所有「触发时间晚于当前时间」且「类型为普通笔记」的闹钟笔记
        Cursor c = context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,                // 笔记内容提供者的URI
                PROJECTION,                             // 查询的字段投影
                // 查询条件：ALERTED_DATE > 当前时间 且 笔记类型=普通笔记
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[]{String.valueOf(currentDate)}, // 条件参数：当前时间（防止SQL注入）
                null                                     // 排序方式：默认排序
        );

        // 2. 遍历查询结果，为每个未触发的闹钟重新注册到系统闹钟管理器
        if (c != null) { // 判空：避免Cursor为空导致空指针
            // 判断是否有查询结果
            if (c.moveToFirst()) {
                do {
                    // 获取当前行的闹钟触发时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    // 获取当前行的笔记ID
                    long noteId = c.getLong(COLUMN_ID);

                    // 3. 构建闹钟触发的意图：指定触发时要启动的广播接收器（AlarmReceiver）
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 为意图设置唯一标识（拼接笔记ID到URI），保证每个闹钟的PendingIntent唯一
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));

                    // 4. 创建延迟执行的PendingIntent：用于闹钟触发时发送广播
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            context,  // 上下文
                            0,        // 请求码：此处用0，因URI已保证唯一性
                            sender,   // 触发时要发送的意图
                            0         // 标志位：默认0（根据API等级可调整为FLAG_UPDATE_CURRENT等）
                    );

                    // 5. 获取系统闹钟管理器服务
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    // 6. 注册闹钟到系统：设置为「RTC_WAKEUP」模式（实时时钟唤醒模式，会唤醒设备）
                    // 参数说明：
                    // - RTC_WAKEUP：基于系统实时时钟，触发时唤醒设备
                    // - alertDate：闹钟触发时间
                    // - pendingIntent：触发时执行的意图
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext()); // 遍历下一条结果
            }
            // 7. 关闭Cursor：释放数据库游标资源，避免内存泄漏
            c.close();
        }
    }
}
