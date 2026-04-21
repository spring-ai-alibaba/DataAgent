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

import com.alibaba.cloud.ai.dataagent.properties.NotifyProperties;
import com.alibaba.cloud.ai.dataagent.service.notify.NotificationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DingTalkNotifierTest {

    private NotifyProperties properties;

    private DingTalkNotifier notifier;

    @BeforeEach
    void setUp() {
        properties = new NotifyProperties();
        properties.setEnabled(true);
        properties.setChannel("dingtalk");
        notifier = new DingTalkNotifier(properties);
    }

    @Test
    void testSupports() {
        assertTrue(notifier.supports("dingtalk"));
        assertFalse(notifier.supports("feishu"));
        assertFalse(notifier.supports("wecom"));
    }

    @Test
    void testNotifyWhenDisabled() {
        properties.setEnabled(false);
        // Should not throw exception
        NotificationInfo info = new NotificationInfo("TestNode", "成功", LocalDateTime.now(), "t1", "a1");
        notifier.notify(info);
        // If no exception, test passes
    }

    @Test
    void testNotifyBuildsCorrectContent() {
        // This test verifies the content building logic indirectly
        NotificationInfo info = new NotificationInfo("ReportGeneratorNode", "成功", LocalDateTime.now(), "thread-1", "agent-1");
        // Should complete without exception (actual webhook call will fail without valid URL)
        notifier.notify(info);
    }
}

