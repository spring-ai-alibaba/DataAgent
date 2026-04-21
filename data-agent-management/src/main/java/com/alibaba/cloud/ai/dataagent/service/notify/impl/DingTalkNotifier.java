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
import com.alibaba.cloud.ai.dataagent.service.notify.NotifierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
@Component
public class DingTalkNotifier implements NotifierService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${spring.ai.alibaba.data-agent.notify.dingtalk.webhook-url:}")
    private String webhookUrl;

    @Value("${spring.ai.alibaba.data-agent.notify.dingtalk.secret-key:}")
    private String secretKey;

    @Override
    public String getName() {
        return "dingtalk";
    }

    @Override
    public void notify(NotificationInfo info) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.debug("DingTalk webhook-url is not configured, skipping notification");
            return;
        }

        String markdownContent = buildMarkdownContent(info);
        sendDingTalkMessage(markdownContent);
    }

    public void notify(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.debug("DingTalk webhook-url is not configured, skipping notification");
            return;
        }

        String markdownContent = """
            ## DataAgent 任务通知

            """ + message;
        sendDingTalkMessage(markdownContent);
    }

    private String buildMarkdownContent(NotificationInfo info) {
        String statusEmoji = "成功".equals(info.getStatus()) ? "✓" : "✗";
        return String.format("""
            ## DataAgent 任务通知

            - **触发节点**: %s
            - **状态**: %s %s
            - **时间**: %s
            """, info.getNodeName() != null ? info.getNodeName() : "N/A",
                info.getStatus(), statusEmoji,
                info.getTimestamp().format(FORMATTER));
    }

    private void sendDingTalkMessage(String markdownContent) {
        try {
            long timestamp = Instant.now().toEpochMilli();
            String sign = computeSign(timestamp, secretKey);
            String urlWithSign = webhookUrl + "&timestamp=" + timestamp + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);

            String requestBody = String.format("""
                {
                    "msgtype": "markdown",
                    "markdown": {
                        "title": "DataAgent 任务通知",
                        "text": %s
                    }
                }
                """, toJsonString(markdownContent));

            String response = WebClient.create(urlWithSign)
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            log.info("DingTalk response: {}", response);
        }
        catch (WebClientResponseException e) {
            log.error("DingTalk API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        }
        catch (Exception e) {
            log.error("Failed to send DingTalk notification", e);
        }
    }


    private String toJsonString(String text) {
        return "\"" + text.replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String computeSign(long timestamp, String secretKey) throws Exception {
        String stringToSign = timestamp + "\n" + secretKey;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }
}
