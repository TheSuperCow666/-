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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框类
 * 继承自 AlertDialog，用于弹出日期时间选择弹窗，支持选择年、月、日、时、分，
 * 并通过回调将选中的时间返回给调用方
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    // 用于存储当前选中的日期时间
    private Calendar mDate = Calendar.getInstance();
    // 是否使用24小时制显示时间
    private boolean mIs24HourView;
    // 日期时间选择完成后的回调接口实例
    private OnDateTimeSetListener mOnDateTimeSetListener;
    // 日期时间选择器核心控件
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间选择完成的回调接口
     * 定义选中时间后触发的方法，将选中的时间戳返回
     */
    public interface OnDateTimeSetListener {
        /**
         * 日期时间选择完成时调用
         * @param dialog 当前的对话框实例
         * @param date 选中的日期时间对应的时间戳（毫秒）
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造方法：创建日期时间选择对话框
     * @param context 上下文对象
     * @param date 初始显示的日期时间（时间戳，毫秒）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        // 初始化日期时间选择器控件
        mDateTimePicker = new DateTimePicker(context);
        // 将选择器设置为对话框的核心视图
        setView(mDateTimePicker);
        
        // 设置日期时间选择器的变更监听：当选择的时间发生变化时更新mDate
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            /**
             * 日期时间选择器内容变更时的回调方法
             * @param view 触发变更的DateTimePicker控件
             * @param year 选中的年
             * @param month 选中的月（Calendar.MONTH 从0开始，0=1月）
             * @param dayOfMonth 选中的日
             * @param hourOfDay 选中的小时（24小时制）
             * @param minute 选中的分钟
             */
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                // 更新Calendar对象的年、月、日、时、分
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新对话框标题为当前选中的时间
                updateTitle(mDate.getTimeInMillis());
            }
        });
        
        // 初始化Calendar为传入的初始时间，并将秒数置为0（只关注时分）
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        // 设置日期时间选择器的初始显示时间
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        
        // 设置对话框的确认按钮，点击事件由当前类的onClick处理
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        // 设置对话框的取消按钮，点击事件为null（无额外逻辑）
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        // 根据系统设置初始化24小时制/12小时制显示
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        // 初始化对话框标题为初始时间
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否使用24小时制显示时间
     * @param is24HourView true=24小时制，false=12小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间选择完成后的回调监听
     * @param callBack 回调接口实例
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框的标题为指定时间戳对应的格式化字符串
     * @param date 要显示的时间戳（毫秒）
     */
    private void updateTitle(long date) {
        // 定义日期时间格式化标识：显示年、日、时间
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        // 根据是否24小时制补充格式化标识（注：此处原代码逻辑有小问题，FORMAT_24HOUR赋值重复，
        // 实际意图应为：24小时制时添加FORMAT_24HOUR，否则不添加）
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : 0;
        // 格式化时间并设置为对话框标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 对话框确认按钮点击事件处理
     * @param arg0 对话框实例
     * @param arg1 按钮标识（确认按钮的索引）
     */
    public void onClick(DialogInterface arg0, int arg1) {
        // 如果设置了回调监听，则触发回调并传递选中的时间戳
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}
