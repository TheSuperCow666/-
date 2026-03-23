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
 * 表示网络操作失败时抛出的受检异常。
 * 例如在同步过程中网络连接异常、请求超时等情况使用。
 */
public class NetworkFailureException extends Exception {
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 构造一个无详细消息的异常。
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 构造一个带有指定详细消息的异常。
     * @param paramString 详细消息
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 构造一个带有指定详细消息和原因的异常。
     * @param paramString 详细消息
     * @param paramThrowable 原因
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}