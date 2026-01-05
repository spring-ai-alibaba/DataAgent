/*
 * Copyright 2024-2025 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 智能体知识的向量资源和文件资源管理
@Slf4j
@Component
public class AgentKnowledgeResourceManager {

	private final TextSplitter textSplitter;

	private final FileStorageService fileStorageService;

	private final AgentVectorStoreService agentVectorStoreService;

	public AgentKnowledgeResourceManager(TextSplitter textSplitter, FileStorageService fileStorageService,
			AgentVectorStoreService agentVectorStoreService) {
		this.textSplitter = textSplitter;
		this.fileStorageService = fileStorageService;
		this.agentVectorStoreService = agentVectorStoreService;
	}

	public void doEmbedingToVectorStore(AgentKnowledge agentKnowledge) throws Exception {
		// delete old data
		this.deleteFromVectorStore(agentKnowledge.getAgentId(), agentKnowledge.getId());

		if (KnowledgeType.QA.equals(agentKnowledge.getType()) || KnowledgeType.FAQ.equals(agentKnowledge.getType())) {
			processQaKnowledge(agentKnowledge);
		}
		else if (KnowledgeType.DOCUMENT.equals(agentKnowledge.getType())) {
			processDocumentKnowledge(agentKnowledge);
		}
		else if (KnowledgeType.ENUM.equals(agentKnowledge.getType())) {
			processEnumKnowledge(agentKnowledge);
		}
		else {
			throw new RuntimeException("Unsupported KnowledgeType: " + agentKnowledge.getType());
		}
	}

	private void processQaKnowledge(AgentKnowledge knowledge) {
		Document document = DocumentConverterUtil.convertQaFaqKnowledgeToDocument(knowledge);
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), List.of(document));
		log.info("Successfully vectorized AgentKnowledge: id={}, type={}", knowledge.getId(), knowledge.getType());
	}

	private void processDocumentKnowledge(AgentKnowledge knowledge) {
		// 处理文档
		List<Document> documents = getAndSplitDocument(knowledge.getFilePath());
		processFileBasedKnowledge(knowledge, documents, "DOCUMENT");
	}

	/**
	 * 处理ENUM类型知识 支持从文件路径或content字段读取数据
	 * @param knowledge 知识对象
	 */
	private void processEnumKnowledge(AgentKnowledge knowledge) {
		List<String> lines;

		if (StringUtils.hasText(knowledge.getFilePath())) {
			// 如果有文件路径，从文件读取
			lines = readFileLines(knowledge.getFilePath());
		}
		else {
			log.error("ENUM type knowledge must have either file path or content, knowledgeId={}", knowledge.getId());
			throw new RuntimeException("ENUM type knowledge must have either file path or content");
		}

		List<Document> documents = parseEnumJsonLines(lines);
		processFileBasedKnowledge(knowledge, documents, "ENUM");
	}

	/**
	 * 解析ENUM类型的JSON行
	 * @param lines JSON行列表
	 */
	@SuppressWarnings("unchecked")
	private List<Document> parseEnumJsonLines(List<String> lines) {
		List<Document> documents = new ArrayList<>();
		for (String line : lines) {
			if (line.isBlank()) {
				continue;
			}

			// 尝试解析为JSON
			List<Map<String, Object>> docMapList = JsonUtil.parse(line, List.class);
			if (docMapList == null || docMapList.isEmpty()) {
				continue;
			}

			for (Map<String, Object> docMap : docMapList) {
				if (docMap == null) {
					continue;
				}

				Object enumName = docMap.get(DocumentMetadataConstant.NAME);
				if (enumName != null) {
					docMap.put(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, KnowledgeType.ENUM);
					documents.add(new Document(String.valueOf(enumName), docMap));
				}

				textSplitter.apply(documents);
			}
		}

		return documents;
	}

	/**
	 * 处理基于文件的知识类型的公共逻辑
	 * @param knowledge 知识对象
	 * @param documents 解析后的文档列表
	 * @param typeName 知识类型名称（用于日志）
	 */
	private void processFileBasedKnowledge(AgentKnowledge knowledge, List<Document> documents, String typeName) {
		if (documents == null || documents.isEmpty()) {
			log.error("No documents extracted from file: knowledgeId={}, filePath={}, type={}", knowledge.getId(),
					knowledge.getFilePath(), typeName);
			throw new RuntimeException("No documents extracted from file");
		}

		// 使用工具类为文档添加元数据
		List<Document> documentsWithMetadata = DocumentConverterUtil
			.convertAgentKnowledgeDocumentsWithMetadata(documents, knowledge);

		// 添加到向量存储
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), documentsWithMetadata);
		log.info("Successfully vectorized {} knowledge: id={}, filePath={}, documentCount={}", typeName,
				knowledge.getId(), knowledge.getFilePath(), documentsWithMetadata.size());
	}

	private List<Document> getAndSplitDocument(String filePath) {
		// 使用FileStorageService获取文件资源对象
		Resource resource = fileStorageService.getFileResource(filePath);

		// 使用TikaDocumentReader读取文件
		TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
		List<Document> documents = tikaDocumentReader.read();

		return textSplitter.apply(documents);
	}

	private List<String> readFileLines(String filePath) {
		Resource resource = fileStorageService.getFileResource(filePath);
		TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
		List<Document> documents = tikaDocumentReader.read();

		List<String> lines = new ArrayList<>();
		for (Document doc : documents) {
			String content = doc.getText();
			if (StringUtils.hasText(content)) {
				lines.add(content);
			}
		}
		return lines;
	}

	/**
	 * 从向量存储中删除知识
	 * @param agentId 代理ID
	 * @param knowledgeId 知识ID
	 * @return 是否删除成功（如果资源不存在也视为成功，实现等幂操作）
	 */
	public boolean deleteFromVectorStore(Integer agentId, Integer knowledgeId) {
		try {

			Map<String, Object> metadata = new HashMap<>();
			metadata.put(Constant.AGENT_ID, agentId.toString());
			metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledgeId);

			agentVectorStoreService.deleteDocumentsByMetedata(agentId.toString(), metadata);
			log.info("Successfully deleted knowledge from vector store, knowledgeId: {}", knowledgeId);
			return true;

		}
		catch (Exception e) {
			// 检查是否是资源不存在的错误，如果是则视为删除成功（等幂操作）
			if (e.getMessage() != null && (e.getMessage().contains("not found")
					|| e.getMessage().contains("does not exist") || e.getMessage().contains("already deleted"))) {
				log.info("Vector data already deleted or not found for knowledgeId: {}, treating as success",
						knowledgeId);
				return true;
			}
			else {
				log.error("Failed to delete knowledge from vector store, knowledgeId: {}", knowledgeId, e);
				return false;
			}
		}
	}

	/**
	 * 删除知识文件
	 * @param knowledge 知识对象
	 * @return 是否删除成功（如果不是文档类型或文件不存在也视为成功）
	 */
	public boolean deleteKnowledgeFile(AgentKnowledge knowledge) {
		// 只有DOCUMENT或ENUM类型且有文件路径的知识才需要删除文件
		if ((!KnowledgeType.DOCUMENT.equals(knowledge.getType()) && !KnowledgeType.ENUM.equals(knowledge.getType()))
				|| !StringUtils.hasText(knowledge.getFilePath())) {
			log.info("Not a document/enum type or no file path, knowledgeId: {}, treating as success",
					knowledge.getId());
			return true;
		}

		try {
			boolean fileDeleted = fileStorageService.deleteFile(knowledge.getFilePath());
			if (fileDeleted) {
				log.info("Successfully deleted knowledge file, filePath: {}", knowledge.getFilePath());
				return true;
			}
			else {
				log.error("Failed to delete knowledge file, filePath: {}", knowledge.getFilePath());
				return false;
			}

		}
		catch (Exception e) {
			// 检查是否是文件不存在的错误，如果是则视为删除成功（等幂操作）
			if (e.getMessage() != null
					&& (e.getMessage().contains("not found") || e.getMessage().contains("does not exist")
							|| e.getMessage().contains("already deleted") || e.getMessage().contains("No such file"))) {
				log.info("File already deleted or not found, filePath: {}, treating as success",
						knowledge.getFilePath());
				return true;
			}
			else {
				log.error("Exception when deleting knowledge file, filePath: {}", knowledge.getFilePath(), e);
				return false;
			}
		}
	}

}
