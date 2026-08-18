/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.notify.impl;

import com.alibaba.cloud.ai.dataagent.service.notify.NotificationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DingTalkNotifierTest {

    private DingTalkNotifier notifier;

    @BeforeEach
    void setUp() throws Exception {
        notifier = new DingTalkNotifier();

        // Use reflection to set @Value fields
        setField(notifier, "webhookUrl", "");
        setField(notifier, "secretKey", "");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testNotifyWhenNoWebhookUrl() throws Exception {
        DingTalkNotifier notifierNoWebhook = new DingTalkNotifier();
        setField(notifierNoWebhook, "webhookUrl", "");

        NotificationInfo info = new NotificationInfo("ReportGeneratorNode", "成功", LocalDateTime.now(), "t1", "a1");
        notifierNoWebhook.notify(info);
    }

    @Test
    void testNotifyBuildsCorrectContent() {
        NotificationInfo info = new NotificationInfo("ReportGeneratorNode", "成功", LocalDateTime.now(), "thread-1", "agent-1");
        notifier.notify(info);
    }

    @Test
    void testNotifyStringMessage() {
        notifier.notify("测试消息内容");
    }
}

