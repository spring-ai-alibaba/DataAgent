package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.service.business.BusinessKnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

import java.util.List;

@RestController
@RequestMapping("/api/business-knowledge")
@CrossOrigin(origins = "*")
public class BusinessKnowledgeController {

	private final BusinessKnowledgeService businessKnowledgeService;

	public BusinessKnowledgeController(BusinessKnowledgeService businessKnowledgeService) {
		this.businessKnowledgeService = businessKnowledgeService;
	}

	@GetMapping
	public ResponseEntity<List<BusinessKnowledge>> list(
			@RequestParam(value = "agentId", required = false) String agentIdStr,
			@RequestParam(value = "keyword", required = false) String keyword) {
		List<BusinessKnowledge> result;
		Long agentId = null;
		try {
			agentId = Long.parseLong(agentIdStr);
		}
		catch (Exception e) {
			// ignore
		}
		if (StringUtils.hasText(keyword) && agentId != null) {
			result = businessKnowledgeService.searchKnowledge(agentId, keyword);
		}
		else if (agentId != null) {
			result = businessKnowledgeService.getKnowledge(agentId);
		}
		else {
			result = businessKnowledgeService.getAllKnowledge();
		}
		return ResponseEntity.ok(result);
	}

	@GetMapping("/{id}")
	public ResponseEntity<BusinessKnowledge> get(@PathVariable(value = "id") Long id) {
		BusinessKnowledge knowledge = businessKnowledgeService.getKnowledgeById(id);
		if (knowledge == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(knowledge);
	}

	@PostMapping
	public ResponseEntity<BusinessKnowledge> create(@RequestBody BusinessKnowledgeDTO knowledge) {
		Long id = businessKnowledgeService.addKnowledge(knowledge);
		return this.get(id);
	}

	@PutMapping("/{id}")
	public ResponseEntity<BusinessKnowledge> update(@PathVariable(value = "id") Long id,
			@RequestBody BusinessKnowledgeDTO knowledge) {
		if (businessKnowledgeService.getKnowledgeById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		businessKnowledgeService.updateKnowledge(id, knowledge);
		return ResponseEntity.ok(businessKnowledgeService.getKnowledgeById(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable(value = "id") Long id) {
		if (businessKnowledgeService.getKnowledgeById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		businessKnowledgeService.deleteKnowledge(id);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/recall/{id}")
	public ResponseEntity<Boolean> recallKnowledge(@PathVariable(value = "id") Long id,
			@RequestParam(value = "isRecall") boolean isRecall) {
		businessKnowledgeService.recallKnowledge(id, isRecall);
		return ResponseEntity.ok().body(true);
	}

}
