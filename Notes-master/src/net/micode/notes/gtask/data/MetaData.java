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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 特殊的任务，用于存储元数据（即本地便签的扩展属性，如背景色、提醒时间等）。
 * 元数据会被序列化为 JSON 字符串，存储在 GTask 任务的备注字段中。
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();

    private String mRelatedGid = null;   // 关联的 GTask 任务 ID

    /**
     * 设置元数据
     * 注意：此方法会直接修改传入的 metaInfo 对象，若该对象在其他地方被共享，可能产生副作用。
     * @param gid 关联的 GTask ID
     * @param metaInfo 元数据 JSON 对象（包含本地便签的扩展属性）
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        setNotes(metaInfo.toString());      // 将整个 JSON 作为备注保存
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    public String getRelatedGid() {
        return mRelatedGid;
    }

    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程 JSON 解析，提取关联的 GTask ID
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    // 以下方法不应被调用，因为元数据不参与常规的本地 JSON 转换
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }
}