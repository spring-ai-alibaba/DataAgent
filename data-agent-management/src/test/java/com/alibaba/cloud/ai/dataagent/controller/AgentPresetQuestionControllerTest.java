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

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentPresetQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPresetQuestionControllerTest {

	@Mock
	private AgentPresetQuestionService presetQuestionService;

	private AgentPresetQuestionController controller;

	@BeforeEach
	void setUp() {
		controller = new AgentPresetQuestionController(presetQuestionService);
	}

	@Test
	void getPresetQuestions_success_returnsQuestions() {
		AgentPresetQuestion q = new AgentPresetQuestion(1L, "What is revenue?", 1);
		when(presetQuestionService.findAllByAgentId(1L)).thenReturn(List.of(q));

		ResponseEntity<List<AgentPresetQuestion>> result = controller.getPresetQuestions(1L);

		assertEquals(200, result.getStatusCode().value());
		assertEquals(1, result.getBody().size());
		assertEquals("What is revenue?", result.getBody().get(0).getQuestion());
	}

	@Test
	void getPresetQuestions_serviceThrows_returns500() {
		when(presetQuestionService.findAllByAgentId(1L)).thenThrow(new RuntimeException("db error"));

		ResponseEntity<List<AgentPresetQuestion>> result = controller.getPresetQuestions(1L);

		assertEquals(500, result.getStatusCode().value());
	}

	@Test
	void savePresetQuestions_success_returnsOk() {
		List<Map<String, Object>> data = List.of(Map.of("question", "Test?", "isActive", true));

		ResponseEntity<Map<String, String>> result = controller.savePresetQuestions(1L, data);

		assertEquals(200, result.getStatusCode().value());
		verify(presetQuestionService).batchSave(eq(1L), anyList());
	}

	@Test
	void savePresetQuestions_booleanStringIsActive_parsesCorrectly() {
		List<Map<String, Object>> data = List.of(Map.of("question", "Test?", "isActive", "true"));

		ResponseEntity<Map<String, String>> result = controller.savePresetQuestions(1L, data);

		assertEquals(200, result.getStatusCode().value());
		verify(presetQuestionService).batchSave(eq(1L), anyList());
	}

	@Test
	void savePresetQuestions_nullIsActive_defaultsToTrue() {
		Map<String, Object> questionData = new java.util.HashMap<>();
		questionData.put("question", "Test?");
		questionData.put("isActive", null);
		List<Map<String, Object>> data = List.of(questionData);

		ResponseEntity<Map<String, String>> result = controller.savePresetQuestions(1L, data);

		assertEquals(200, result.getStatusCode().value());
		verify(presetQuestionService).batchSave(eq(1L), anyList());
	}

	@Test
	void savePresetQuestions_serviceThrows_returns500() {
		List<Map<String, Object>> data = List.of(Map.of("question", "Test?", "isActive", true));
		doThrow(new RuntimeException("db error")).when(presetQuestionService).batchSave(eq(1L), anyList());

		ResponseEntity<Map<String, String>> result = controller.savePresetQuestions(1L, data);

		assertEquals(500, result.getStatusCode().value());
		assertTrue(result.getBody().containsKey("error"));
	}

	@Test
	void deletePresetQuestion_success_returnsOk() {
		ResponseEntity<Map<String, String>> result = controller.deletePresetQuestion(1L, 10L);

		assertEquals(200, result.getStatusCode().value());
		verify(presetQuestionService).deleteById(10L);
	}

	@Test
	void deletePresetQuestion_serviceThrows_returns500() {
		doThrow(new RuntimeException("db error")).when(presetQuestionService).deleteById(10L);

		ResponseEntity<Map<String, String>> result = controller.deletePresetQuestion(1L, 10L);

		assertEquals(500, result.getStatusCode().value());
		assertTrue(result.getBody().containsKey("error"));
	}

}
