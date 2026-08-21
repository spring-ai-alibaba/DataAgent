/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox;

import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemoryOutboxService {

	private final MemoryOutboxMapper mapper;

	public void enqueue(String aggregateType, String aggregateId, String eventType, String payload) {
		mapper.insert(MemoryOutboxEvent.builder()
			.aggregateType(aggregateType)
			.aggregateId(aggregateId)
			.eventType(eventType)
			.payload(payload)
			.build());
	}

}
