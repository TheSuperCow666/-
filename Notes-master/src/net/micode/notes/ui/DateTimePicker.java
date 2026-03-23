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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 日期时间选择器控件，继承自FrameLayout，支持24小时/12小时制切换，
 * 包含日期、小时、分钟、AM/PM选择器，可监听日期时间变更事件
 * @author MiCode Open Source Community
 * @date 2010-2011
 */
public class DateTimePicker extends FrameLayout {

    // 默认启用状态
    private static final boolean DEFAULT_ENABLE_STATE = true;

    // 时间常量定义
    private static final int HOURS_IN_HALF_DAY = 12;      // 半天小时数（12小时制）
    private static final int HOURS_IN_ALL_DAY = 24;        // 全天小时数（24小时制）
    private static final int DAYS_IN_ALL_WEEK = 7;         // 一周天数
    private static final int DATE_SPINNER_MIN_VAL = 0;     // 日期选择器最小值
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1; // 日期选择器最大值
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0; // 24小时制小时选择器最小值
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23; // 24小时制小时选择器最大值
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1; // 12小时制小时选择器最小值
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12; // 12小时制小时选择器最大值
    private static final int MINUT_SPINNER_MIN_VAL = 0;    // 分钟选择器最小值
    private static final int MINUT_SPINNER_MAX_VAL = 59;   // 分钟选择器最大值
    private static final int AMPM_SPINNER_MIN_VAL = 0;     // AM/PM选择器最小值
    private static final int AMPM_SPINNER_MAX_VAL = 1;     // AM/PM选择器最大值

    // 选择器控件实例
    private final NumberPicker mDateSpinner;       // 日期选择器（周维度）
    private final NumberPicker mHourSpinner;       // 小时选择器
    private final NumberPicker mMinuteSpinner;     // 分钟选择器
    private final NumberPicker mAmPmSpinner;       // AM/PM选择器（12小时制）
    private Calendar mDate;                        // 当前选中的日期时间

    // 日期选择器显示的文本值（一周7天的格式化文本）
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    // 12小时制下的上午/下午标识：true=AM（上午），false=PM（下午）
    private boolean mIsAm;

    // 24小时制标识：true=24小时制，false=12小时制
    private boolean mIs24HourView;

    // 控件整体启用状态
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    // 初始化标识：避免初始化过程中触发重复的事件回调
    private boolean mInitialising;

    // 日期时间变更监听器
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    /**
     * 日期选择器值变更监听器
     * 当日期选择器值变化时，更新选中日期并触发变更回调
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据选择器值变化量，调整日期（按天增减）
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            // 更新日期选择器显示
            updateDateControl();
            // 触发日期时间变更回调
            onDateTimeChanged();
        }
    };

    /**
     * 小时选择器值变更监听器
     * 处理小时变化逻辑，包括跨天、12/24小时制切换、AM/PM切换
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false; // 日期是否变更标识
            Calendar cal = Calendar.getInstance();

            // 12小时制处理逻辑
            if (!mIs24HourView) {
                // 跨天场景1：下午(PM) 11点 -> 12点，日期+1
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 跨天场景2：上午(AM) 12点 -> 11点，日期-1
                else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }

                // 11点↔12点切换时，切换AM/PM状态
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl(); // 更新AM/PM选择器显示
                }
            }
            // 24小时制处理逻辑
            else {
                // 跨天场景1：23点 -> 0点，日期+1
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 跨天场景2：0点 -> 23点，日期-1
                else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 计算并设置新的小时值（转换为24小时制）
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);

            // 触发日期时间变更回调
            onDateTimeChanged();

            // 若日期发生变更，更新年/月/日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟选择器值变更监听器
     * 处理分钟变化逻辑，包括跨小时、跨天场景
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0; // 小时偏移量（跨小时时调整）

            // 跨小时场景1：59分 -> 0分，小时+1
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            }
            // 跨小时场景2：0分 -> 59分，小时-1
            else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }

            // 若存在小时偏移，调整日期时间并更新相关控件
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour()); // 更新小时选择器值
                updateDateControl(); // 更新日期选择器显示

                // 根据新小时值更新AM/PM状态
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                } else {
                    mIsAm = true;
                }
                updateAmPmControl(); // 更新AM/PM选择器显示
            }

            // 设置新的分钟值并触发变更回调
            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * AM/PM选择器值变更监听器
     * 切换AM/PM时，调整小时值（±12小时）并更新显示
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 切换AM/PM状态
            mIsAm = !mIsAm;
            // 调整小时：AM→PM +12小时，PM→AM -12小时
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            // 更新AM/PM选择器显示
            updateAmPmControl();
            // 触发日期时间变更回调
            onDateTimeChanged();
        }
    };

    /**
     * 日期时间变更监听器接口
     * 当日期、小时、分钟等发生变化时触发回调
     */
    public interface OnDateTimeChangedListener {
        /**
         * 日期时间变更回调方法
         * @param view 触发变更的DateTimePicker实例
         * @param year 变更后的年
         * @param month 变更后的月（Calendar.MONTH，0~11）
         * @param dayOfMonth 变更后的日
         * @param hourOfDay 变更后的小时（24小时制，0~23）
         * @param minute 变更后的分钟
         */
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                int dayOfMonth, int hourOfDay, int minute);
    }

    /**
     * 构造方法：使用当前系统时间初始化
     * @param context 上下文
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造方法：使用指定时间初始化
     * @param context 上下文
     * @param date 初始时间（毫秒级时间戳）
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 核心构造方法：初始化控件、选择器、监听器
     * @param context 上下文
     * @param date 初始时间（毫秒级时间戳）
     * @param is24HourView 是否使用24小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance(); // 初始化日期实例
        mInitialising = true; // 标记为初始化状态

        // 初始化AM/PM状态：根据当前小时判断（≥12为PM）
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;

        // 加载布局文件
        inflate(context, R.layout.datetime_picker, this);

        // 绑定选择器控件
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mMinuteSpinner = (NumberPicker) findViewById(R.id.minute);
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);

        // 初始化日期选择器
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // 初始化小时选择器（绑定监听器）
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // 初始化分钟选择器
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setLongPressUpdateInterval(100); // 长按更新间隔（100ms）
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // 初始化AM/PM选择器
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings(); // 获取系统AM/PM文本（如"上午"/"下午"）
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm); // 设置显示文本
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新控件到初始状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置24小时制/12小时制
        set24HourView(is24HourView);

        // 设置初始时间
        setCurrentDate(date);

        // 设置控件启用状态
        setEnabled(isEnabled());

        // 初始化完成
        mInitialising = false;
    }

    /**
     * 设置控件整体启用状态
     * @param enabled true=启用，false=禁用
     */
    @Override
    public void setEnabled(boolean enabled) {
        // 状态未变化则直接返回
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        // 同步设置所有选择器的启用状态
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        // 更新启用状态标识
        mIsEnabled = enabled;
    }

    /**
     * 获取控件整体启用状态
     * @return true=启用，false=禁用
     */
    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前选中的日期时间（毫秒级时间戳）
     * @return 时间戳（ms）
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前选中的日期时间（通过时间戳）
     * @param date 时间戳（ms）
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        // 解析时间戳为年/月/日/时/分并设置
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 设置当前选中的日期时间（通过年/月/日/时/分）
     * @param year 年
     * @param month 月（Calendar.MONTH，0~11）
     * @param dayOfMonth 日
     * @param hourOfDay 小时（24小时制，0~23）
     * @param minute 分钟
     */
    public void setCurrentDate(int year, int month,
            int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    /**
     * 获取当前选中的年
     * @return 年
     */
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    /**
     * 设置当前选中的年
     * @param year 年
     */
    public void setCurrentYear(int year) {
        // 初始化中或值未变化则返回
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl(); // 更新日期选择器显示
        onDateTimeChanged(); // 触发变更回调
    }

    /**
     * 获取当前选中的月
     * @return 月（Calendar.MONTH，0~11）
     */
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    /**
     * 设置当前选中的月
     * @param month 月（Calendar.MONTH，0~11）
     */
    public void setCurrentMonth(int month) {
        // 初始化中或值未变化则返回
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl(); // 更新日期选择器显示
        onDateTimeChanged(); // 触发变更回调
    }

    /**
     * 获取当前选中的日
     * @return 日（1~31）
     */
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 设置当前选中的日
     * @param dayOfMonth 日（1~31）
     */
    public void setCurrentDay(int dayOfMonth) {
        // 初始化中或值未变化则返回
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl(); // 更新日期选择器显示
        onDateTimeChanged(); // 触发变更回调
    }

    /**
     * 获取当前选中的小时（24小时制）
     * @return 小时（0~23）
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 内部方法：获取适配选择器显示的小时值（根据12/24小时制转换）
     * @return 12小时制：1~12；24小时制：0~23
     */
    private int getCurrentHour() {
        if (mIs24HourView) {
            return getCurrentHourOfDay(); // 24小时制直接返回
        } else {
            int hour = getCurrentHourOfDay();
            // 12小时制转换：13→1，0→12
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    /**
     * 设置当前选中的小时（24小时制）
     * @param hourOfDay 小时（0~23）
     */
    public void setCurrentHour(int hourOfDay) {
        // 初始化中或值未变化则返回
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);

        // 12小时制下，更新AM/PM状态和小时显示值
        if (!mIs24HourView) {
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false; // ≥12为PM
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY; // 转换为12小时制显示值（13→1）
                }
            } else {
                mIsAm = true; // <12为AM
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY; // 0→12
                }
            }
            updateAmPmControl(); // 更新AM/PM选择器显示
        }

        // 设置小时选择器值并触发变更回调
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的分钟
     * @return 分钟（0~59）
     */
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    /**
     * 设置当前选中的分钟
     * @param minute 分钟（0~59）
     */
    public void setCurrentMinute(int minute) {
        // 初始化中或值未变化则返回
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute); // 设置分钟选择器值
        mDate.set(Calendar.MINUTE, minute); // 更新日期实例
        onDateTimeChanged(); // 触发变更回调
    }

    /**
     * 判断是否为24小时制
     * @return true=24小时制，false=12小时制
     */
    public boolean is24HourView() {
        return mIs24HourView;
    }

    /**
     * 设置是否使用24小时制
     * @param is24HourView true=24小时制，false=12小时制
     */
    public void set24HourView(boolean is24HourView) {
        // 状态未变化则返回
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;

        // 显示/隐藏AM/PM选择器
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);

        // 更新小时选择器范围并重置小时值
        int hour = getCurrentHourOfDay();
        updateHourControl();
        setCurrentHour(hour);
        updateAmPmControl();
    }

    /**
     * 内部方法：更新日期选择器的显示文本（一周7天的格式化文本）
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        // 计算起始日期：当前日期 - 4天（使选中项居中）
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);

        // 清空原有显示值
        mDateSpinner.setDisplayedValues(null);

        // 生成一周7天的显示文本（格式：月.日 星期X）
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }

        // 设置日期选择器显示文本并居中选中
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        mDateSpinner.invalidate(); // 强制重绘
    }

    /**
     * 内部方法：更新AM/PM选择器的显示状态
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            // 24小时制隐藏AM/PM选择器
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            // 12小时制设置AM/PM值并显示
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 内部方法：更新小时选择器的取值范围（根据12/24小时制）
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            // 24小时制：0~23
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            // 12小时制：1~12
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 设置日期时间变更监听器
     * @param callback 监听器实例（null则取消监听）
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 内部方法：触发日期时间变更回调
     * 若监听器已设置，则调用其onDateTimeChanged方法
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}
