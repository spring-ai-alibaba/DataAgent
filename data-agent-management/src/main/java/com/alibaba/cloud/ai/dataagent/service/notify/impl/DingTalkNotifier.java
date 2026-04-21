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
