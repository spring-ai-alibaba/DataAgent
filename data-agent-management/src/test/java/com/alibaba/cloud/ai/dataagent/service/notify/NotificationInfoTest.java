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
package com.alibaba.cloud.ai.dataagent.service.notify;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationInfoTest {

    @Test
    void testDefaultConstructor() {
        NotificationInfo info = new NotificationInfo();
        assertNull(info.getNodeName());
        assertNull(info.getStatus());
        assertNull(info.getTimestamp());
        assertNull(info.getThreadId());
        assertNull(info.getAgentId());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        NotificationInfo info = new NotificationInfo("ReportGeneratorNode", "成功", now, "thread-123", "agent-456");

        assertEquals("ReportGeneratorNode", info.getNodeName());
        assertEquals("成功", info.getStatus());
        assertEquals(now, info.getTimestamp());
        assertEquals("thread-123", info.getThreadId());
        assertEquals("agent-456", info.getAgentId());
    }

    @Test
    void testSettersAndGetters() {
        NotificationInfo info = new NotificationInfo();
        LocalDateTime now = LocalDateTime.now();

        info.setNodeName("TestNode");
        info.setStatus("失败");
        info.setTimestamp(now);
        info.setThreadId("thread-789");
        info.setAgentId("agent-101");

        assertEquals("TestNode", info.getNodeName());
        assertEquals("失败", info.getStatus());
        assertEquals(now, info.getTimestamp());
        assertEquals("thread-789", info.getThreadId());
        assertEquals("agent-101", info.getAgentId());
    }
}

