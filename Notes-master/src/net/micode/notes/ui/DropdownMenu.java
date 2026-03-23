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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单封装类
 * 基于Button和PopupMenu实现，点击Button弹出下拉菜单，支持菜单点击事件监听、菜单选项查找、按钮标题设置
 */
public class DropdownMenu {
    // 下拉菜单的触发按钮
    private Button mButton;
    // 弹出式菜单核心控件
    private PopupMenu mPopupMenu;
    // PopupMenu对应的Menu对象，用于操作菜单项
    private Menu mMenu;

    /**
     * 构造方法，初始化下拉菜单
     * @param context 上下文对象，用于创建PopupMenu
     * @param button  触发下拉菜单的按钮
     * @param menuId  菜单资源文件ID（如R.menu.xxx），用于加载菜单布局
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        // 绑定触发按钮
        mButton = button;
        // 设置按钮背景为下拉图标
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        // 创建PopupMenu对象，关联按钮和上下文
        mPopupMenu = new PopupMenu(context, mButton);
        // 获取Menu对象，用于后续操作菜单项
        mMenu = mPopupMenu.getMenu();
        // 加载菜单资源文件，初始化菜单项
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        
        // 给按钮设置点击事件，点击时显示下拉菜单
        mButton.setOnClickListener(new OnClickListener() {
            /**
             * 按钮点击回调方法
             * @param v 被点击的View（即当前Button）
             */
            public void onClick(View v) {
                // 显示弹出式菜单
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置下拉菜单项的点击事件监听器
     * @param listener 菜单项点击事件回调接口实现类
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        // 判空，避免空指针异常
        if (mPopupMenu != null) {
            // 给PopupMenu设置菜单项点击监听
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据菜单项ID查找对应的MenuItem对象
     * @param id 菜单项的资源ID（如R.id.menu_item_xxx）
     * @return 找到的MenuItem对象，若未找到则返回null
     */
    public MenuItem findItem(int id) {
        // 通过Menu对象查找指定ID的菜单项
        return mMenu.findItem(id);
    }

    /**
     * 设置下拉菜单触发按钮的显示标题
     * @param title 要显示的标题文本
     */
    public void setTitle(CharSequence title) {
        // 设置Button的显示文本
        mButton.setText(title);
    }
}
