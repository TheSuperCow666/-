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

package net.micode.notes.gtask.exception;

/**
 * 表示操作执行失败时抛出的运行时异常。
 * 例如在数据提交、网络请求或同步过程中发生不可恢复的错误时使用。
 */
public class ActionFailureException extends RuntimeException {
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 构造一个无详细消息的异常。
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 构造一个带有指定详细消息的异常。
     * @param paramString 详细消息
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 构造一个带有指定详细消息和原因的异常。
     * @param paramString 详细消息
     * @param paramThrowable 原因
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}