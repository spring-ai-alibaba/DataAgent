/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.entity.AgentKnowledge;
import com.alibaba.cloud.ai.service.knowledge.AgentKnowledgeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Knowledge Management Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/agent-knowledge")
@CrossOrigin(origins = "*")
@AllArgsConstructor
// todo: 功能完成后需要与前端接入，部分返回值需要定义DTO
public class AgentKnowledgeController {

	private final AgentKnowledgeService agentKnowledgeService;

	/**
	 * Query knowledge list by agent ID
	 */
	@GetMapping("/agent/{agentId}")
	public ResponseEntity<Map<String, Object>> getKnowledgeByAgentId(@PathVariable(value = "agentId") Integer agentId,
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword) {

		Map<String, Object> response = new HashMap<>();

		try {
			List<AgentKnowledge> knowledgeList;

			if (keyword != null && !keyword.trim().isEmpty()) {
				// Search knowledge
				knowledgeList = agentKnowledgeService.searchKnowledge(agentId, keyword.trim());
			}
			else if (type != null && !type.trim().isEmpty()) {
				// Filter by type
				knowledgeList = agentKnowledgeService.getKnowledgeByType(agentId, type);
			}
			else if (status != null && !status.trim().isEmpty()) {
				// Filter by status
				knowledgeList = agentKnowledgeService.getKnowledgeByStatus(agentId, status);
			}
			else {
				// Query all knowledge
				knowledgeList = agentKnowledgeService.getKnowledgeByAgentId(agentId);
			}

			response.put("success", true);
			response.put("data", knowledgeList);
			response.put("total", knowledgeList.size());
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			response.put("success", false);
			response.put("message", "查询知识列表失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Query knowledge details by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Map<String, Object>> getKnowledgeById(@PathVariable("id") Integer id) {
		Map<String, Object> response = new HashMap<>();

		try {
			AgentKnowledge knowledge = agentKnowledgeService.getKnowledgeById(id);
			if (knowledge != null) {
				response.put("success", true);
				response.put("data", knowledge);
				return ResponseEntity.ok(response);
			}
			else {
				response.put("success", false);
				response.put("message", "知识不存在");
				return ResponseEntity.notFound().build();
			}
		}
		catch (Exception e) {
			log.error("查询知识详情失败：{}", e.getMessage());
			response.put("success", false);
			response.put("message", "查询知识详情失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Create knowledge
	 */
	@PostMapping
	public ResponseEntity<Map<String, Object>> createKnowledge(@RequestBody AgentKnowledge knowledge) {
		Map<String, Object> response = new HashMap<>();

		try {
			// Validate required fields
			if (knowledge.getAgentId() == null) {
				response.put("success", false);
				response.put("message", "智能体ID不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			if (knowledge.getTitle() == null || knowledge.getTitle().trim().isEmpty()) {
				response.put("success", false);
				response.put("message", "知识标题不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			// Create knowledge in database
			agentKnowledgeService.createKnowledge(knowledge);

			// If knowledge content is not empty and status is active, add to vector store
			if (knowledge.getContent() != null && !knowledge.getContent().trim().isEmpty()
					&& "active".equals(knowledge.getStatus())) {
				try {
					agentKnowledgeService.addKnowledgeToVectorStore(Long.valueOf(knowledge.getAgentId()), knowledge);
					// Update embedding status to completed
					knowledge.setEmbeddingStatus("completed");
					agentKnowledgeService.updateKnowledge(knowledge.getId(), knowledge);
				}
				catch (Exception vectorException) {
					// Vector storage failed, update embedding status to failed
					knowledge.setEmbeddingStatus("failed");
					agentKnowledgeService.updateKnowledge(knowledge.getId(), knowledge);
					// Log but don't affect main process
					response.put("vectorWarning", "知识已保存，但向量化失败：" + vectorException.getMessage());
				}
			}

			response.put("success", true);
			response.put("data", knowledge);
			response.put("message", "知识创建成功");
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			log.error("创建知识失败：{}", e.getMessage());
			response.put("success", false);
			response.put("message", "创建知识失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Update knowledge
	 */
	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> updateKnowledge(@PathVariable("id") Integer id,
			@RequestBody AgentKnowledge knowledge) {

		Map<String, Object> response = new HashMap<>();

		try {
			// Validate required fields
			if (knowledge.getTitle() == null || knowledge.getTitle().trim().isEmpty()) {
				response.put("success", false);
				response.put("message", "知识标题不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			// First get original knowledge information
			AgentKnowledge originalKnowledge = agentKnowledgeService.getKnowledgeById(id);
			if (originalKnowledge == null) {
				response.put("success", false);
				response.put("message", "知识不存在");
				return ResponseEntity.notFound().build();
			}

			// Update knowledge in database
			boolean st = agentKnowledgeService.updateKnowledge(id, knowledge);
			if (st) {
				// Handle vector storage update
				AgentKnowledge updatedKnowledge = agentKnowledgeService.getKnowledgeById(id);
				try {
					Long agentId = Long.valueOf(updatedKnowledge.getAgentId());

					// If content changes or status becomes active, need to re-vectorize
					boolean contentChanged = !java.util.Objects.equals(originalKnowledge.getContent(),
							updatedKnowledge.getContent());
					boolean statusChangedToActive = !"active".equals(originalKnowledge.getStatus())
							&& "active".equals(updatedKnowledge.getStatus());
					boolean statusChangedFromActive = "active".equals(originalKnowledge.getStatus())
							&& !"active".equals(updatedKnowledge.getStatus());

					if (statusChangedFromActive) {
						// Status changes from active to other, delete vector data
						agentKnowledgeService.deleteKnowledgeFromVectorStore(agentId, id);
						updatedKnowledge.setEmbeddingStatus("pending");
					}
					else if ((contentChanged || statusChangedToActive) && "active".equals(updatedKnowledge.getStatus())
							&& updatedKnowledge.getContent() != null
							&& !updatedKnowledge.getContent().trim().isEmpty()) {
						// Content changes or status becomes active, re-vectorize
						agentKnowledgeService.deleteKnowledgeFromVectorStore(agentId, id); // First
						// delete
						// old
						agentKnowledgeService.addKnowledgeToVectorStore(agentId, updatedKnowledge); // Then
						// add
						// new
						updatedKnowledge.setEmbeddingStatus("completed");
						agentKnowledgeService.updateKnowledge(id, updatedKnowledge); // Update
																						// embedding
																						// status
					}
				}
				catch (Exception vectorException) {
					// Vector storage operation failed, update embedding status to failed
					updatedKnowledge.setEmbeddingStatus("failed");
					agentKnowledgeService.updateKnowledge(id, updatedKnowledge);
					response.put("vectorWarning", "知识已更新，但向量化失败：" + vectorException.getMessage());
				}

				response.put("success", true);
				response.put("data", updatedKnowledge);
				response.put("message", "知识更新成功");
				return ResponseEntity.ok(response);
			}
			else {
				response.put("success", false);
				response.put("message", "知识更新失败");
				return ResponseEntity.badRequest().body(response);
			}
		}
		catch (Exception e) {
			log.error("更新知识失败：{}", e.getMessage());
			response.put("success", false);
			response.put("message", "更新知识失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Delete knowledge
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> deleteKnowledge(@PathVariable("id") Integer id) {
		Map<String, Object> response = new HashMap<>();

		try {
			// First get knowledge information, used to delete vector data
			AgentKnowledge knowledge = agentKnowledgeService.getKnowledgeById(id);
			if (knowledge == null) {
				response.put("success", false);
				response.put("message", "知识不存在");
				return ResponseEntity.notFound().build();
			}

			// Delete knowledge in database
			boolean deleted = agentKnowledgeService.deleteKnowledge(id);
			if (deleted) {
				// Also delete vector data
				try {
					Long agentId = Long.valueOf(knowledge.getAgentId());
					agentKnowledgeService.deleteKnowledgeFromVectorStore(agentId, id);
				}
				catch (Exception vectorException) {
					// Vector deletion failed, log warning but don't affect main process
					response.put("vectorWarning", "知识已删除，但向量数据删除失败：" + vectorException.getMessage());
				}

				response.put("success", true);
				response.put("message", "知识删除成功");
				return ResponseEntity.ok(response);
			}
			else {
				response.put("success", false);
				response.put("message", "知识删除失败");
				return ResponseEntity.badRequest().body(response);
			}
		}
		catch (Exception e) {
			response.put("success", false);
			response.put("message", "删除知识失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Batch update knowledge status
	 */
	@PutMapping("/batch/status")
	public ResponseEntity<Map<String, Object>> batchUpdateStatus(@RequestBody Map<String, Object> request) {

		Map<String, Object> response = new HashMap<>();

		try {
			@SuppressWarnings("unchecked")
			List<Integer> ids = (List<Integer>) request.get("ids");
			String status = (String) request.get("status");

			if (ids == null || ids.isEmpty()) {
				response.put("success", false);
				response.put("message", "ID列表不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			if (status == null || status.trim().isEmpty()) {
				response.put("success", false);
				response.put("message", "状态不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			boolean st = agentKnowledgeService.batchUpdateStatus(ids, status);
			response.put("success", st);
			response.put("message", "批量更新成功");
			response.put("updatedCount", ids.size());
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			log.error("批量更新知识失败：{}", e.getMessage());
			response.put("success", false);
			response.put("message", "批量更新失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Get agent knowledge statistics
	 */
	@GetMapping("/statistics/{agentId}")
	public ResponseEntity<Map<String, Object>> getKnowledgeStatistics(@PathVariable("agentId") Integer agentId) {
		Map<String, Object> response = new HashMap<>();

		try {
			int totalCount = agentKnowledgeService.countKnowledgeByAgent(agentId);
			List<Object[]> typeStatistics = agentKnowledgeService.countKnowledgeByType(agentId);

			Map<String, Object> statistics = new HashMap<>();
			statistics.put("totalCount", totalCount);
			statistics.put("typeStatistics", typeStatistics);

			response.put("success", true);
			response.put("data", statistics);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			log.error("获取统计信息失败：{}", e.getMessage());
			response.put("success", false);
			response.put("message", "获取统计信息失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

}
