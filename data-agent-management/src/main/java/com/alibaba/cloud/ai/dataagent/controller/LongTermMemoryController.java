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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.memory.CreateMemoryItemRequest;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import com.alibaba.cloud.ai.dataagent.service.memory.longterm.LongTermMemoryService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Explicit review API for durable cross-session memory. Creation never makes a memory
 * prompt-visible; a separate confirmation is required.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agents/{agentId}/memories")
public class LongTermMemoryController {

	private final LongTermMemoryService memoryService;

	@GetMapping
	public ApiResponse<List<MemoryItem>> list(@PathVariable Integer agentId,
			@RequestParam(required = false) MemoryStatus status) {
		return ApiResponse.success("success list memories", memoryService.list(agentId, status));
	}

	@PostMapping
	public ApiResponse<MemoryItem> createCandidate(@PathVariable Integer agentId,
			@Valid @RequestBody CreateMemoryItemRequest request) {
		MemoryItem item = MemoryItem.builder()
			.scopeType(request.getScopeType())
			.agentId(agentId)
			.datasourceId(request.getDatasourceId())
			.memoryKind(request.getMemoryKind())
			.memoryKey(request.getMemoryKey())
			.valueJson(request.getValue().toString())
			.sourceTurnId(request.getSourceTurnId())
			.confidence(request.getConfidence() == null ? BigDecimal.ONE : request.getConfidence())
			.schemaFingerprint(request.getSchemaFingerprint())
			.validUntil(request.getValidUntil())
			.supersedesId(request.getSupersedesId())
			.build();
		return ApiResponse.success("success create memory candidate", memoryService.createCandidate(item));
	}

	@PostMapping("/{memoryId}/confirm")
	public ApiResponse<MemoryItem> confirm(@PathVariable Integer agentId, @PathVariable Long memoryId) {
		MemoryItem item = requireAgentMemory(agentId, memoryId);
		return ApiResponse.success("success confirm memory", memoryService.confirm(item.getId()));
	}

	@PostMapping("/{memoryId}/invalidate")
	public ApiResponse<MemoryItem> invalidate(@PathVariable Integer agentId, @PathVariable Long memoryId) {
		MemoryItem item = requireAgentMemory(agentId, memoryId);
		return ApiResponse.success("success invalidate memory", memoryService.invalidate(item.getId()));
	}

	private MemoryItem requireAgentMemory(Integer agentId, Long memoryId) {
		MemoryItem item = memoryService.findById(memoryId);
		if (!agentId.equals(item.getAgentId())) {
			throw new IllegalArgumentException("Memory item does not belong to agent " + agentId);
		}
		return item;
	}

}
