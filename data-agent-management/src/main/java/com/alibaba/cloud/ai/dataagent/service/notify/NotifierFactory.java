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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotifierFactory {

    @Value("${spring.ai.alibaba.data-agent.notify.channel:dingtalk}")
    private String defaultChannel;

    private final Map<String, NotifierService> notifierMap;

    public NotifierFactory(List<NotifierService> notifierServices) {
        this.notifierMap = notifierServices.stream()
            .collect(Collectors.toMap(NotifierService::getName, Function.identity(), (a, b) -> a));
    }

    public NotifierService getByName(String name) {
        NotifierService notifier = notifierMap.get(name);
        if (notifier == null) {
            throw new IllegalArgumentException("Notifier not found: " + name);
        }
        return notifier;
    }

    public NotifierService getDefault() {
        NotifierService notifier = notifierMap.get(defaultChannel);
        if (notifier == null) {
            throw new IllegalArgumentException("Default notifier not found for channel: " + defaultChannel);
        }
        return notifier;
    }
}

