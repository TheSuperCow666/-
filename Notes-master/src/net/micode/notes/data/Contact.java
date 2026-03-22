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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人查询工具类（小米便签原版逻辑）
 * 核心功能：根据电话号码查询系统联系人姓名，通过HashMap缓存结果避免重复查询系统数据库，提升性能
 * 应用场景：通话记录类便签中，将电话号码转换为对应的联系人名称显示
 * 注意：本类为静态工具类，所有方法/属性均为static，无需实例化
 */
public class Contact {
    // 联系人缓存容器：key=原始电话号码，value=匹配到的联系人姓名
    // 作用：缓存查询结果，避免重复访问系统联系人数据库，提升查询效率
    private static HashMap<String, String> sContactCache;

    // 日志标签：用于Logcat调试时定位本类的日志信息
    private static final String TAG = "Contact";

    // 联系人查询条件模板：
    // 1. PHONE_NUMBERS_EQUAL：兼容不同格式的电话号码匹配（如带/不带区号、空格）
    // 2. 限定MIMETYPE为Phone类型：只查询电话号码类联系人，排除邮箱等其他类型
    // 3. 关联phone_lookup表：通过min_match字段精准匹配电话号码（+为占位符，后续替换）
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码查询对应的联系人姓名
     * @param context Android上下文：用于访问系统ContentResolver（联系人数据库属于系统级ContentProvider）
     * @param phoneNumber 需要匹配的电话号码（原始格式，无需提前格式化）
     * @return 匹配到的联系人姓名（查询成功）/ null（无匹配联系人/查询异常）
     */
    public static String getContact(Context context, String phoneNumber) {
        // 懒加载初始化缓存容器：首次调用时创建HashMap，避免空指针
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 缓存命中校验：若该号码已查询过，直接返回缓存结果，无需访问数据库
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 替换查询条件模板中的占位符：
        // toCallerIDMinMatch将原始号码转换为系统识别的最小匹配格式（如去除分隔符、统一区号）
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 访问系统联系人ContentProvider：
        // 1. URI：Data.CONTENT_URI（联系人总表）
        // 2. 查询列：仅Phone.DISPLAY_NAME（联系人显示名），减少数据传输
        // 3. 查询条件：替换后的selection
        // 4. 查询参数：原始电话号码
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 处理查询结果：
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 读取联系人姓名（游标第0列，对应Phone.DISPLAY_NAME）
                String name = cursor.getString(0);
                // 将结果存入缓存，供后续查询复用
                sContactCache.put(phoneNumber, name);
                // 返回匹配到的联系人姓名
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 异常捕获：游标越界（如查询结果为空但moveToFirst返回true）
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 最终操作：关闭游标，释放系统资源（必须执行，避免内存泄漏）
                cursor.close();
            }
        } else {
            // 无匹配结果：日志提示，返回null
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}