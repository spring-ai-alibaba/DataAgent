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
package com.alibaba.cloud.ai.dataagent.workflow.node.rag;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.EvidenceRecallNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceRecallNodeTest {

	private static final String LLM_REWRITE_RESPONSE = """
			{
			    "standalone_query": "查询上周销售额数据"
			}
			""";

	@Mock
	private LlmService llmService;

	@Mock
	private AgentVectorStoreService vectorStoreService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	private EvidenceRecallNode evidenceRecallNode;

	@BeforeEach
	void setUp() {
		evidenceRecallNode = new EvidenceRecallNode(llmService, vectorStoreService, jsonParseUtil,
				agentKnowledgeMapper);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(INPUT_KEY, "查询上周销售额", AGENT_ID, "1"));
	}

	private Document createBusinessTermDocument(String content) {
		return new Document(content,
				Map.of(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM));
	}

	private Document createAgentKnowledgeDocument(String content, int knowledgeId, String knowledgeType) {
		return new Document(content,
				Map.of(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE,
						DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledgeId,
						DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, knowledgeType));
	}

	@Test
	void apply_validQuery_returnsEvidence() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		List<Document> businessDocs = List.of(createBusinessTermDocument("销售额=sum(order_amount)"));
		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.BUSINESS_TERM)))
			.thenReturn(businessDocs);
		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.AGENT_KNOWLEDGE)))
			.thenReturn(new ArrayList<>());

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_multipleDocTypes_formatsAllEvidence() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		List<Document> businessDocs = List.of(createBusinessTermDocument("PV=页面浏览量"));

		Document faqDoc = createAgentKnowledgeDocument("什么是PV", 1, KnowledgeType.FAQ.getCode());
		List<Document> knowledgeDocs = List.of(faqDoc);

		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setTitle("PV定义");
		knowledge.setContent("PV是Page View的缩写");

		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.BUSINESS_TERM)))
			.thenReturn(businessDocs);
		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.AGENT_KNOWLEDGE)))
			.thenReturn(knowledgeDocs);
		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_faqKnowledge_formatsAsFaq() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		Document faqDoc = createAgentKnowledgeDocument("退款怎么算", 2, KnowledgeType.FAQ.getCode());

		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(2);
		knowledge.setTitle("退款FAQ");
		knowledge.setContent("只统计已入库退货");

		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.BUSINESS_TERM)))
			.thenReturn(new ArrayList<>());
		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.AGENT_KNOWLEDGE)))
			.thenReturn(List.of(faqDoc));
		when(agentKnowledgeMapper.selectById(2)).thenReturn(knowledge);

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_documentKnowledge_formatsAsDocument() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		Document docKnowledge = createAgentKnowledgeDocument("华东地区销售数据增长20%", 3, KnowledgeType.DOCUMENT.getCode());

		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(3);
		knowledge.setTitle("2025Q3报告");
		knowledge.setSourceFilename("销售数据.md");
		knowledge.setContent("详细报告内容");

		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.BUSINESS_TERM)))
			.thenReturn(new ArrayList<>());
		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(),
				org.mockito.ArgumentMatchers.eq(DocumentMetadataConstant.AGENT_KNOWLEDGE)))
			.thenReturn(List.of(docKnowledge));
		when(agentKnowledgeMapper.selectById(3)).thenReturn(knowledge);

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_llmRewriteFailure_usesOriginalQuery() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString())).thenThrow(new RuntimeException("LLM timeout"));

		assertThrows(RuntimeException.class, () -> evidenceRecallNode.apply(state));
	}

	@Test
	void apply_vectorStoreFailure_returnsEmptyEvidence() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(), anyString()))
			.thenThrow(new RuntimeException("Vector store connection failed"));

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_jsonParseFailure_usesOriginalQuery() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("not valid json at all")));

		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class)))
			.thenThrow(new RuntimeException("JSON parse error"));

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_noEvidenceFound_returnsEmptyString() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(), anyString()))
			.thenReturn(new ArrayList<>());

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

	@Test
	void apply_withMultiTurnContext_includesContext() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(MULTI_TURN_CONTEXT, "user: 查询PV, assistant: PV是页面浏览量"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(LLM_REWRITE_RESPONSE)));

		when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(), anyString()))
			.thenReturn(new ArrayList<>());

		Map<String, Object> result = evidenceRecallNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(EVIDENCE));
		assertNotNull(result.get(EVIDENCE));
	}

}
