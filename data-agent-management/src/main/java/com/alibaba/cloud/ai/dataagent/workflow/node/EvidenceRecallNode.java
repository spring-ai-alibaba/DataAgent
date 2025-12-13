/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.common.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.common.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.common.enums.TextType;
import com.alibaba.cloud.ai.dataagent.common.util.*;
import com.alibaba.cloud.ai.dataagent.dto.prompt.EvidenceQueryRewriteDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.FluxConverter;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

@Slf4j
@Component
@AllArgsConstructor
public class EvidenceRecallNode implements NodeAction {

	private final LlmService llmService;

	private final AgentVectorStoreService vectorStoreService;

	private final JsonParseUtil jsonParseUtil;

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 从state中提取question和agentId
		String question = StateUtil.getStringValue(state, INPUT_KEY);
		String agentId = StateUtil.getStringValue(state, AGENT_ID);
		Assert.hasText(agentId, "Agent ID cannot be empty.");

		log.info("Rewriting query before getting evidence in question: {}", question);
		log.debug("Agent ID: {}", agentId);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建查询重写提示
		String prompt = PromptHelper.buildEvidenceQueryRewritePrompt(multiTurn, question);
		log.debug("Built evidence-query-rewrite prompt as follows \n {} \n", prompt);

		// 调用LLM进行查询重写
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
		Sinks.Many<String> evidenceDisplaySink = Sinks.many().multicast().onBackpressureBuffer();

		final Map<String, Object> resultMap = new HashMap<>();
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在查询重写以更好召回evidence..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n查询重写完成！")),
				result -> {
					resultMap.putAll(getEvidences(result, agentId, evidenceDisplaySink));
					return resultMap;
				});

		Flux<GraphResponse<StreamingOutput>> evidenceFlux = FluxConverter.builder()
			.startingNode(this.getClass().getSimpleName())
			.startingState(state)
			.mapResult(r -> resultMap)
			.build(evidenceDisplaySink.asFlux().map(ChatResponseUtil::createPureResponse));
		return Map.of(EVIDENCE, generator.concatWith(evidenceFlux));
	}

	private Map<String, Object> getEvidences(String llmOutput, String agentId, Sinks.Many<String> sink) {
		try {
			String standaloneQuery = extractStandaloneQuery(llmOutput);

			if (null == standaloneQuery || standaloneQuery.isEmpty()) {
				log.debug("No standalone query from LLM output");
				sink.tryEmitNext("未能进行查询重写！\n");
				return Map.of(EVIDENCE, "无");
			}

			// 输出重写后的查询
			outputRewrittenQuery(standaloneQuery, sink);

			// 获取业务知识和智能体知识文档
			DocumentRetrievalResult retrievalResult = retrieveDocuments(agentId, standaloneQuery);

			// 检查是否有证据文档
			if (retrievalResult.allDocuments().isEmpty()) {
				log.debug("No evidence documents found for agent: {} with query: {}", agentId, standaloneQuery);
				sink.tryEmitNext("未找到证据！\n");
				return Map.of(EVIDENCE, "无");
			}

			// 构建证据内容
			String evidence = buildFormattedEvidenceContent(retrievalResult.businessTermDocuments(),
					retrievalResult.agentKnowledgeDocuments());
			log.info("Evidence content built as follows \n {} \n", evidence);
			// 输出证据内容
			outputEvidenceContent(retrievalResult.allDocuments(), sink);

			// 返回结果
			return Map.of(EVIDENCE, evidence);
		}
		catch (Exception e) {
			log.error("Error occurred while getting evidences", e);
			sink.tryEmitError(e);
			return Map.of(EVIDENCE, "");
		}
		finally {
			sink.tryEmitComplete();
		}
	}

	private void outputRewrittenQuery(String standaloneQuery, Sinks.Many<String> sink) {
		sink.tryEmitNext("重写后查询：\n");
		sink.tryEmitNext(standaloneQuery + "\n");
		log.debug("Using standalone query for evidence recall: {}", standaloneQuery);
		sink.tryEmitNext("正在获取证据...");
	}

	private DocumentRetrievalResult retrieveDocuments(String agentId, String standaloneQuery) {
		// 获取业务知识文档
		List<Document> businessTermDocuments = vectorStoreService
			.getDocumentsForAgent(agentId, standaloneQuery, DocumentMetadataConstant.BUSINESS_TERM)
			.stream()
			.toList();

		// 获取智能体知识文档
		List<Document> agentKnowledgeDocuments = vectorStoreService
			.getDocumentsForAgent(agentId, standaloneQuery, DocumentMetadataConstant.AGENT_KNOWLEDGE)
			.stream()
			.toList();

		// 合并所有证据文档
		List<Document> allDocuments = new ArrayList<>();
		if (!businessTermDocuments.isEmpty())
			allDocuments.addAll(businessTermDocuments);
		if (!agentKnowledgeDocuments.isEmpty())
			allDocuments.addAll(agentKnowledgeDocuments);

		// 添加文档检索日志
		log.info("Retrieved documents for agent {}: {} business term docs, {} agent knowledge docs, total {} docs",
				agentId, businessTermDocuments.size(), agentKnowledgeDocuments.size(), allDocuments.size());

		return new DocumentRetrievalResult(businessTermDocuments, agentKnowledgeDocuments, allDocuments);
	}

	// 构建证据内容，输出格式
	// 1. [来源: 2025Q3报告-销售数据.md] ...华东地区的增长主要来自于核心用户...
	// 2. [来源: 客服FAQ] Q: 退款怎么算? A: 只统计已入库退货...
	private String buildFormattedEvidenceContent(List<Document> businessTermDocuments,
			List<Document> agentKnowledgeDocuments) {
		// 构建业务知识内容
		String businessKnowledgeContent = buildBusinessKnowledgeContent(businessTermDocuments);

		// 构建智能体知识内容
		String agentKnowledgeContent = buildAgentKnowledgeContent(agentKnowledgeDocuments);

		// 使用PromptHelper的模板方法进行渲染
		String businessPrompt = PromptHelper.buildBusinessKnowledgePrompt(businessKnowledgeContent);
		String agentPrompt = PromptHelper.buildAgentKnowledgePrompt(agentKnowledgeContent);

		// 添加证据构建日志
		log.info("Building evidence content: business knowledge length {}, agent knowledge length {}",
				businessKnowledgeContent.length(), agentKnowledgeContent.length());

		// 拼接业务知识和智能体知识作为证据
		return businessKnowledgeContent.isEmpty() && agentKnowledgeContent.isEmpty() ? "无"
				: businessPrompt + (agentKnowledgeContent.isEmpty() ? "" : "\n\n" + agentPrompt);
	}

	private String buildBusinessKnowledgeContent(List<Document> businessTermDocuments) {
		if (businessTermDocuments.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		// 直接使用Document的完整内容，每行一个Document
		for (Document doc : businessTermDocuments) {
			result.append(doc.getText()).append("\n");
		}

		return result.toString();
	}

	private String buildAgentKnowledgeContent(List<Document> agentKnowledgeDocuments) {
		if (agentKnowledgeDocuments.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		for (int i = 0; i < agentKnowledgeDocuments.size(); i++) {
			Document doc = agentKnowledgeDocuments.get(i);
			Map<String, Object> metadata = doc.getMetadata();
			String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);

			// 根据知识类型调用不同的处理方法
			if (KnowledgeType.FAQ.getCode().equals(knowledgeType) || KnowledgeType.QA.getCode().equals(knowledgeType)) {
				processFaqOrQaKnowledge(doc, i, result);
			}
			else {
				processDocumentKnowledge(doc, i, result);
			}
		}

		return result.toString();
	}

	/**
	 * 处理FAQ或QA类型的知识
	 */
	private void processFaqOrQaKnowledge(Document doc, int index, StringBuilder result) {
		Map<String, Object> metadata = doc.getMetadata();
		String content = doc.getText();
		Integer knowledgeId = (Integer) metadata.get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
		String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);

		log.debug("Processing {} type knowledge with id: {}", knowledgeType, knowledgeId);

		if (knowledgeId != null) {
			try {
				AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
				if (knowledge != null) {
					String title = knowledge.getTitle();
					// 格式：[来源: xxx] Q: xxx A: xxx
					result.append(index + 1).append(". [来源: ");
					result.append(title.isEmpty() ? "知识库" : title);
					result.append("] Q: ").append(content).append(" A: ").append(knowledge.getContent()).append("\n");

					log.debug("Successfully processed {} knowledge with title: {}", knowledgeType, title);
				}
				else {
					log.warn("Knowledge not found for id: {}", knowledgeId);
				}
			}
			catch (Exception e) {
				log.error("Error getting knowledge by id: {}", knowledgeId, e);
				// 如果获取失败，使用原始内容
				result.append(index + 1).append(". [来源: 知识库] ").append(content).append("\n");
			}
		}
		else {
			// 如果没有知识ID，使用原始内容
			log.error("No knowledge id found for agent knowledge document: {}", doc.getId());
			result.append(index + 1).append(". [来源: 知识库] ").append(content).append("\n");
		}
	}

	/**
	 * 处理DOCUMENT类型的知识
	 */
	private void processDocumentKnowledge(Document doc, int index, StringBuilder result) {
		Map<String, Object> metadata = doc.getMetadata();
		String content = doc.getText();
		Integer knowledgeId = (Integer) metadata.get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
		String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);
		String title = "";
		String sourceFilename = "";

		log.debug("Processing {} type knowledge with id: {}", knowledgeType, knowledgeId);

		if (knowledgeId != null) {
			try {
				AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
				if (knowledge != null) {
					title = knowledge.getTitle();
					sourceFilename = knowledge.getSourceFilename();

					log.debug("Successfully processed {} knowledge with title: {}, source file: {}", knowledgeType,
							title, sourceFilename);
				}
				else {
					log.warn("Knowledge not found for id: {}", knowledgeId);
				}
			}
			catch (Exception e) {
				log.error("Error getting knowledge by id: {}", knowledgeId, e);
			}
		}

		// 构建来源信息，格式为"标题-文件名"
		String sourceInfo = title.isEmpty() ? "文档" : title;
		if (!sourceFilename.isEmpty()) {
			sourceInfo += "-" + sourceFilename;
		}

		result.append(index + 1).append(". [来源: ");
		result.append(sourceInfo);
		result.append("] ").append(content).append("\n");
	}

	private void outputEvidenceContent(List<Document> allDocuments, Sinks.Many<String> sink) {
		if (allDocuments.isEmpty()) {
			return;
		}

		log.info("Outputting evidence content for {} documents", allDocuments.size());
		sink.tryEmitNext("已找到 " + allDocuments.size() + " 条相关证据文档，如下是文档的部分信息\n");

		// 只输出文档的摘要信息，而不是完整内容
		for (int i = 0; i < allDocuments.size(); i++) {
			Document doc = allDocuments.get(i);
			String content = doc.getText();

			// 限制每个文档摘要的长度，最多显示100个字符
			String summary = content.length() > 100 ? content.substring(0, 100) + "..." : content;

			sink.tryEmitNext(String.format("证据%d: %s\n", i + 1, summary));
		}
	}

	private record DocumentRetrievalResult(List<Document> businessTermDocuments, List<Document> agentKnowledgeDocuments,
			List<Document> allDocuments) {
	}

	private String extractStandaloneQuery(String llmOutput) {
		EvidenceQueryRewriteDTO evidenceQueryRewriteDTO;
		try {
			String content = MarkdownParserUtil.extractText(llmOutput.trim());
			evidenceQueryRewriteDTO = jsonParseUtil.tryConvertToObject(content, EvidenceQueryRewriteDTO.class);
			log.info("For getting evidence, successfully parsed EvidenceQueryRewriteDTO from LLM response: {}",
					evidenceQueryRewriteDTO);
			return evidenceQueryRewriteDTO.getStandaloneQuery();
		}
		catch (Exception e) {
			log.error("Failed to parse EvidenceQueryRewriteDTO from LLM response", e);
		}
		return null;
	}

}
