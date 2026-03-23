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
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义EditText控件，用于笔记编辑场景
 * 扩展功能包括：
 * 1. 处理链接的上下文菜单点击
 * 2. 自定义回车键/删除键的行为
 * 3. 文本变化监听回调
 * 4. 精准的触摸选中文本逻辑
 */
public class NoteEditText extends EditText {
    // 日志标签
    private static final String TAG = "NoteEditText";
    // 当前EditText在编辑区域中的索引位置
    private int mIndex;
    // 记录删除操作前的光标选中起始位置
    private int mSelectionStartBeforeDelete;

    // 支持的链接协议常量
    private static final String SCHEME_TEL = "tel:";     // 电话协议
    private static final String SCHEME_HTTP = "http:";   // HTTP协议
    private static final String SCHEME_EMAIL = "mailto:";// 邮件协议

    /**
     * 协议与上下文菜单文本资源的映射表
     * key: 协议前缀
     * value: 对应的字符串资源ID
     */
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);    // 电话链接菜单文本
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);   // 网页链接菜单文本
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email); // 邮件链接菜单文本
    }

    /**
     * 文本视图变化监听器接口
     * 由NoteEditActivity调用，用于处理EditText的删除/添加/文本变化事件
     */
    public interface OnTextViewChangeListener {
        /**
         * 当按下删除键且文本为空时，删除当前EditText
         * @param index 当前EditText的索引
         * @param text 当前EditText的文本内容
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键时，在当前EditText后添加新的EditText
         * @param index 新EditText的插入位置索引
         * @param text 回车键后的文本内容
         */
        void onEditTextEnter(int index, String text);

        /**
         * 文本变化时，控制选项的显示/隐藏
         * @param index 当前EditText的索引
         * @param hasText 是否有文本内容（用于判断是否显示选项）
         */
        void onTextChange(int index, boolean hasText);
    }

    // 文本视图变化监听器实例
    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * 构造方法
     * @param context 上下文
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0; // 默认索引为0
    }

    /**
     * 设置当前EditText的索引
     * @param index 索引值
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本视图变化监听器
     * @param listener 监听器实例
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /**
     * 构造方法
     * @param context 上下文
     * @param attrs 属性集
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    /**
     * 构造方法
     * @param context 上下文
     * @param attrs 属性集
     * @param defStyle 样式
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    /**
     * 重写触摸事件处理
     * 实现点击位置精准定位光标
     * @param event 触摸事件
     * @return 事件处理结果（是否消费事件）
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: // 按下事件
                // 计算触摸点的实际坐标（去除内边距，加上滚动偏移）
                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                // 获取文本布局对象，计算触摸点对应的行和偏移量
                Layout layout = getLayout();
                int line = layout.getLineForVertical(y); // 获取触摸点所在行
                int off = layout.getOffsetForHorizontal(line, x); // 获取触摸点在该行的字符偏移量
                Selection.setSelection(getText(), off); // 设置光标位置
                break;
        }

        return super.onTouchEvent(event); // 执行父类的触摸事件处理
    }

    /**
     * 重写按键按下事件处理
     * 监听回车键和删除键的按下动作
     * @param keyCode 按键码
     * @param event 按键事件
     * @return 事件处理结果
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER: // 回车键按下
                if (mOnTextViewChangeListener != null) {
                    return false; // 交由onKeyUp处理
                }
                break;
            case KeyEvent.KEYCODE_DEL: // 删除键按下
                mSelectionStartBeforeDelete = getSelectionStart(); // 记录删除前的光标位置
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event); // 执行父类的按键按下处理
    }

    /**
     * 重写按键抬起事件处理
     * 处理删除键和回车键的核心逻辑
     * @param keyCode 按键码
     * @param event 按键事件
     * @return 事件处理结果
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL: // 删除键抬起
                if (mOnTextViewChangeListener != null) {
                    // 光标在起始位置且不是第一个EditText时，触发删除当前EditText
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true; // 消费事件，阻止默认删除行为
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted"); // 日志提示监听器未设置
                }
                break;
            case KeyEvent.KEYCODE_ENTER: // 回车键抬起
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart(); // 获取光标起始位置
                    // 截取光标后的文本
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 保留光标前的文本
                    setText(getText().subSequence(0, selectionStart));
                    // 触发添加新EditText的回调
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted"); // 日志提示监听器未设置
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event); // 执行父类的按键抬起处理
    }

    /**
     * 重写焦点变化事件处理
     * 焦点变化时触发文本变化回调，控制选项显示/隐藏
     * @param focused 是否获取焦点
     * @param direction 焦点变化方向
     * @param previouslyFocusedRect 上一个焦点区域
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            // 失去焦点且文本为空时，通知隐藏选项；否则通知显示选项
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect); // 执行父类的焦点变化处理
    }

    /**
     * 重写上下文菜单创建事件
     * 为链接文本创建自定义上下文菜单，支持电话/网页/邮件链接的快捷操作
     * @param menu 上下文菜单对象
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        // 仅当文本是Spanned类型（包含富文本/链接）时处理
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart(); // 选中起始位置
            int selEnd = getSelectionEnd();     // 选中结束位置

            // 计算选中区域的最小/最大偏移量（兼容正向/反向选中）
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 获取选中区域内的URLSpan（链接跨度）
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            // 仅当选中区域包含且仅包含一个链接时创建自定义菜单
            if (urls.length == 1) {
                int defaultResId = 0;
                // 根据链接协议匹配对应的菜单文本资源
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 未匹配到已知协议时，使用默认的"其他链接"文本
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 添加自定义菜单项并设置点击事件
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            /**
                             * 菜单项点击事件处理
                             * 触发链接的默认点击行为（打开对应应用）
                             * @param item 被点击的菜单项
                             * @return 是否消费事件
                             */
                            public boolean onMenuItemClick(MenuItem item) {
                                // 触发URLSpan的点击事件（跳转到对应Intent）
                                urls[0].onClick(NoteEditText.this);
                                return true; // 消费事件
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu); // 执行父类的上下文菜单创建逻辑
    }
}
