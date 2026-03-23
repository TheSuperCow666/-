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

import org.json.JSONObject;

/**
 * 同步节点的抽象基类，定义了所有可同步节点（任务、任务列表、元数据）的通用接口。
 */
public abstract class Node {
    /**
     * 同步动作类型常量
     */
    public static final int SYNC_ACTION_NONE = 0;               // 无需同步
    public static final int SYNC_ACTION_ADD_REMOTE = 1;         // 添加远程节点
    public static final int SYNC_ACTION_ADD_LOCAL = 2;          // 添加本地节点
    public static final int SYNC_ACTION_DEL_REMOTE = 3;         // 删除远程节点
    public static final int SYNC_ACTION_DEL_LOCAL = 4;          // 删除本地节点
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;      // 更新远程节点
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;       // 更新本地节点
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;    // 更新冲突
    public static final int SYNC_ACTION_ERROR = 8;              // 同步错误

    private String mGid;            // Google Tasks 中的全局唯一 ID
    private String mName;           // 节点名称
    private long mLastModified;     // 最后修改时间（来自远程）
    private boolean mDeleted;       // 是否已标记删除

    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    /**
     * 生成创建节点的 JSON 动作
     * @param actionId 动作 ID
     * @return JSON 对象，描述创建操作
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成更新节点的 JSON 动作
     * @param actionId 动作 ID
     * @return JSON 对象，描述更新操作
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 从远程 JSON 设置节点内容
     * @param js 远程返回的 JSON 对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 从本地 JSON 设置节点内容（本地格式由 SqlNote.getContent() 生成）
     * @param js 本地便签转换的 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将节点内容转换为本地 JSON 格式（用于更新本地便签）
     * @return JSON 对象，符合 SqlNote.setContent() 的格式
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据本地 Cursor 决定同步动作
     * @param c 游标，指向 note 表的一条记录
     * @return 同步动作常量（如 SYNC_ACTION_UPDATE_LOCAL 等）
     */
    public abstract int getSyncAction(Cursor c);

    // ----- 公共 setter / getter -----
    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }
}