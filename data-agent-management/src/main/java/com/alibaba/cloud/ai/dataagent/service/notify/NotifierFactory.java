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

