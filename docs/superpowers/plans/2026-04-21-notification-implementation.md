# Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add notification functionality that sends DingTalk messages when workflow completes or after human intervention.

**Architecture:** Factory pattern with `NotifierService` interface, `NotifierFactory` creates channel-specific implementations. Default implementation is `DingTalkNotifier` with HmacSHA256 signature verification.

**Tech Stack:** Spring Boot, WebClient (for HTTP calls), standard Java crypto (HmacSHA256)

---

## File Structure

| File | Purpose |
|------|---------|
| `service/notify/NotificationInfo.java` | Notification data object |
| `service/notify/NotifierService.java` | Interface for notifier implementations |
| `service/notify/NotifierFactory.java` | Factory to create notifier by channel |
| `service/notify/impl/DingTalkNotifier.java` | DingTalk implementation |
| `properties/NotifyProperties.java` | Configuration properties |
| `service/graph/GraphServiceImpl.java` | Trigger notifications on completion |

---

## Task 1: NotificationInfo & NotifierService

**Files:**
- Create: `service/notify/NotificationInfo.java`
- Create: `service/notify/NotifierService.java`
- Test: `test/service/notify/NotificationInfoTest.java`

- [ ] **Step 1: Create NotificationInfo.java**

```java
package com.alibaba.cloud.ai.dataagent.service.notify;

import java.time.LocalDateTime;

public class NotificationInfo {

    private String nodeName;

    private String status;

    private LocalDateTime timestamp;

    private String threadId;

    private String agentId;

    public NotificationInfo() {
    }

    public NotificationInfo(String nodeName, String status, LocalDateTime timestamp, String threadId, String agentId) {
        this.nodeName = nodeName;
        this.status = status;
        this.timestamp = timestamp;
        this.threadId = threadId;
        this.agentId = agentId;
    }

    // getters and setters
}
```

- [ ] **Step 2: Create NotifierService.java**

```java
package com.alibaba.cloud.ai.dataagent.service.notify;

public interface NotifierService {

    void notify(NotificationInfo info);

    boolean supports(String channel);
}
```

- [ ] **Step 3: Write unit tests for NotificationInfo**

Run: `./mvnw test -Dtest=NotificationInfoTest`
Expected: Tests pass

---

## Task 2: DingTalkNotifier Implementation

**Files:**
- Create: `service/notify/impl/DingTalkNotifier.java`
- Create: `properties/NotifyProperties.java`
- Test: `test/service/notify/impl/DingTalkNotifierTest.java`

- [ ] **Step 1: Create NotifyProperties.java**

```java
package com.alibaba.cloud.ai.dataagent.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.alibaba.data-agent.notify")
public class NotifyProperties {

    private boolean enabled = false;

    private String channel = "dingtalk";

    private DingTalkConfig dingtalk = new DingTalkConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public DingTalkConfig getDingtalk() {
        return dingtalk;
    }

    public void setDingtalk(DingTalkConfig dingtalk) {
        this.dingtalk = dingtalk;
    }

    public static class DingTalkConfig {

        private String webhookUrl;

        private String secretKey;

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
```

- [ ] **Step 2: Create DingTalkNotifier.java**

```java
package com.alibaba.cloud.ai.dataagent.service.notify.impl;

import com.alibaba.cloud.ai.dataagent.properties.NotifyProperties;
import com.alibaba.cloud.ai.dataagent.service.notify.NotificationInfo;
import com.alibaba.cloud.ai.dataagent.service.notify.NotifierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
@Component
public class DingTalkNotifier implements NotifierService {

    private static final String CHANNEL_NAME = "dingtalk";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotifyProperties properties;

    public DingTalkNotifier(NotifyProperties properties) {
        this.properties = properties;
    }

    @Override
    public void notify(NotificationInfo info) {
        if (!properties.isEnabled()) {
            log.debug("Notification is disabled, skipping");
            return;
        }

        String markdownContent = buildMarkdownContent(info);
        sendDingTalkMessage(markdownContent);
    }

    @Override
    public boolean supports(String channel) {
        return CHANNEL_NAME.equals(channel);
    }

    private String buildMarkdownContent(NotificationInfo info) {
        String statusEmoji = "成功".equals(info.getStatus()) ? "✓" : "✗";
        return String.format("""
            ## DataAgent 任务通知

            - **状态**: %s %s
            - **触发节点**: %s
            - **时间**: %s
            """, info.getStatus(), statusEmoji, info.getNodeName(),
                info.getTimestamp().format(FORMATTER));
    }

    private void sendDingTalkMessage(String markdownContent) {
        try {
            String webhookUrl = properties.getDingtalk().getWebhookUrl();
            String secretKey = properties.getDingtalk().getSecretKey();

            String sign = generateSign(secretKey);
            String urlWithSign = webhookUrl + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);

            String requestBody = String.format("""
                {
                    "msgtype": "markdown",
                    "markdown": {
                        "title": "DataAgent 任务通知",
                        "text": %s
                    }
                }
                """, toJsonString(markdownContent));

            WebClient.create(urlWithSign).post().bodyValue(requestBody).retrieve().bodyToMono(String.class)
                .block();

            log.info("DingTalk notification sent successfully");
        }
        catch (Exception e) {
            log.error("Failed to send DingTalk notification", e);
        }
    }

    private String generateSign(String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String timestamp = String.valueOf(System.currentTimeMillis());
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal((timestamp + "\n" + secretKey).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    private String toJsonString(String text) {
        return "\"" + text.replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
```

- [ ] **Step 3: Write unit tests**

Run: `./mvnw test -Dtest=DingTalkNotifierTest`
Expected: Tests pass

---

## Task 3: NotifierFactory

**Files:**
- Create: `service/notify/NotifierFactory.java`
- Modify: `config/DataAgentConfiguration.java` (add @EnableConfigurationProperties for NotifyProperties)

- [ ] **Step 1: Create NotifierFactory.java**

```java
package com.alibaba.cloud.ai.dataagent.service.notify;

import com.alibaba.cloud.ai.dataagent.properties.NotifyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotifierFactory {

    private final NotifyProperties properties;

    private final Map<String, NotifierService> notifierMap;

    public NotifierFactory(NotifyProperties properties, List<NotifierService> notifierServices) {
        this.properties = properties;
        this.notifierMap = notifierServices.stream()
            .collect(Collectors.toMap(this::extractChannel, Function.identity(), (a, b) -> a));
    }

    private String extractChannel(NotifierService service) {
        // Assume service class name contains channel name
        return service.getClass().getSimpleName().replace("Notifier", "").toLowerCase();
    }

    public NotifierService create() {
        String channel = properties.getChannel();
        NotifierService notifier = notifierMap.get(channel);
        if (notifier == null) {
            log.warn("No notifier found for channel: {}, using first available", channel);
            notifier = notifierMap.values().stream().findFirst().orElse(null);
        }
        return notifier;
    }
}
```

- [ ] **Step 2: Enable NotifyProperties in DataAgentConfiguration**

Add `NotifyProperties.class` to `@EnableConfigurationProperties` annotation.

Run: `./mvnw compile`
Expected: Compiles successfully

---

## Task 4: Configuration & Integration

**Files:**
- Modify: `application.yml` (add notify config)
- Modify: `service/graph/GraphServiceImpl.java` (trigger notifications)

- [ ] **Step 1: Add notify config to application.yml**

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        notify:
          enabled: true
          channel: dingtalk
          dingtalk:
            webhook-url: ${DINGTALK_WEBHOOK_URL:}
            secret-key: ${DINGTALK_SECRET_KEY:}
```

- [ ] **Step 2: Integrate with GraphServiceImpl**

Inject `NotifierFactory` into `GraphServiceImpl`. In `handleStreamComplete()`, create `NotificationInfo` and call `notifierFactory.create().notify(info)`.

In `handleStreamError()`, call with status "失败".

Run: `./mvnw compile`
Expected: Compiles successfully

---

## Task 5: Unit Tests

**Files:**
- Modify: `GraphServiceImplTest.java`

- [ ] **Step 1: Write integration test for notification flow**

Run: `./mvnw test -Dtest=GraphServiceImplTest`
Expected: Tests pass

---

## Task 6: Commit

- [ ] **Step 1: Commit changes**

```bash
git add -A
git commit -m "feat: add notification system with DingTalk support"
```
