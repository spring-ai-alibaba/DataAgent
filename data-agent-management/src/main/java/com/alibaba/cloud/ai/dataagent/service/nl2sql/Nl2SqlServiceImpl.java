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
package com.alibaba.cloud.ai.dataagent.service.nl2sql;

import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.common.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.MarkdownParserUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildMixMacSqlDbPrompt;
import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildMixSelectorPrompt;

@Slf4j
@Service
@AllArgsConstructor
public class Nl2SqlServiceImpl implements Nl2SqlService {

	public final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	@Override
	public Flux<ChatResponse> semanticConsistencyStream(String sql, String queryPrompt) {
		String semanticConsistencyPrompt = PromptHelper.buildSemanticConsistenPrompt(queryPrompt, sql);
		log.info("semanticConsistencyPrompt = {}", semanticConsistencyPrompt);
		return llmService.callUser(semanticConsistencyPrompt);
	}

	@Override
	public Flux<String> generateSql(String evidence, String query, SchemaDTO schemaDTO, String sql,
			String exceptionMessage, DbConfig dbConfig, String executionDescription, String dialect) {
		log.info("Generating SQL for query: {}, hasExistingSql: {}, dialect: {}", query, sql != null && !sql.isEmpty(),
				dialect);

		Flux<String> newSqlFlux;
		if (sql != null && !sql.isEmpty()) {
			// Use professional SQL error repair prompt
			log.debug("Using SQL error fixer for existing SQL: {}", sql);
			String errorFixerPrompt = PromptHelper.buildSqlErrorFixerPrompt(query, schemaDTO, evidence, sql,
					exceptionMessage, executionDescription, dialect);
			newSqlFlux = llmService.toStringFlux(llmService.callUser(errorFixerPrompt));
			log.info("SQL error fixing completed");
		}
		else {
			// Normal SQL generation process
			log.debug("Generating new SQL from scratch");
			List<String> prompts = PromptHelper.buildMixSqlGeneratorPrompt(query, schemaDTO, evidence,
					executionDescription, dialect);
			newSqlFlux = llmService.toStringFlux(llmService.call(prompts.get(0), prompts.get(1)));
			log.info("New SQL generation completed");
		}

		return newSqlFlux;
	}

	/**
	 * Use ChatClient to generate optimized SQL
	 */
	@Override
	public Flux<String> generateOptimizedSql(String previousSql, String exceptionMessage, int round, String dialect) {
		try {
			// todo: 写一个Prompt文件
			StringBuilder prompt = new StringBuilder();
			prompt.append("当前连接的数据库类型是：").append(dialect).append("\n\n");
			prompt.append("请对以下SQL进行第").append(round).append("轮优化:\n\n");
			prompt.append("当前SQL:\n").append(previousSql).append("\n\n");

			if (exceptionMessage != null && !exceptionMessage.trim().isEmpty()) {
				prompt.append("需要解决的问题:\n").append(exceptionMessage).append("\n\n");
			}

			prompt.append("优化目标:\n");
			prompt.append("1. 修复任何语法错误（使用").append(dialect).append("语法规范）\n");
			prompt.append("1. 修复任何语法错误\n");
			prompt.append("2. 提升查询性能\n");
			prompt.append("3. 确保查询安全性\n");
			prompt.append("4. 优化可读性\n\n");
			prompt.append("重要提示：生成的SQL必须符合").append(dialect).append("数据库的语法规范。\n");
			prompt.append("请只返回优化后的SQL语句，不要包含其他说明。");

			return llmService.toStringFlux(llmService.callUser(prompt.toString()));
		}
		catch (Exception e) {
			log.error("使用ChatClient优化SQL失败: {}", e.getMessage());
			return Flux.just(previousSql);
		}
	}

	private Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String sqlGenerateSchemaMissingAdvice,
			Consumer<Set<String>> resultConsumer) {
		log.debug("Fine selecting tables based on advice: {}", sqlGenerateSchemaMissingAdvice);
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		String prompt = " 建议：" + sqlGenerateSchemaMissingAdvice
				+ " \n 请按照建议进行返回相关表的名称，只返回建议中提到的表名，返回格式为：[\"a\",\"b\",\"c\"] \n " + schemaInfo;
		log.debug("Calling LLM for table selection with advice");
		StringBuilder sb = new StringBuilder();
		return llmService.callUser(prompt).doOnNext(r -> {
			String text = r.getResult().getOutput().getText();
			sb.append(text);
		}).doOnComplete(() -> {
			String content = sb.toString();
			if (!content.trim().isEmpty()) {
				String jsonContent = MarkdownParserUtil.extractText(content);
				List<String> tableList;
				try {
					tableList = JsonUtil.getObjectMapper().readValue(jsonContent, new TypeReference<List<String>>() {
					});
				}
				catch (Exception e) {
					log.error("Failed to parse table selection response: {}", jsonContent, e);
					throw new IllegalStateException(jsonContent);
				}
				if (tableList != null && !tableList.isEmpty()) {
					Set<String> selectedTables = tableList.stream()
						.map(String::toLowerCase)
						.collect(Collectors.toSet());
					log.debug("Selected {} tables based on advice: {}", selectedTables.size(), selectedTables);
					resultConsumer.accept(selectedTables);
				}
			}
			log.debug("No tables selected based on advice");
			resultConsumer.accept(new HashSet<>());
		});
	}

	@Override
	public Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String query, String evidence,
			String sqlGenerateSchemaMissingAdvice, DbConfig specificDbConfig, Consumer<SchemaDTO> dtoConsumer) {
		log.debug("Fine selecting schema for query: {} with evidences and specificDbConfig: {}", query,
				specificDbConfig != null ? specificDbConfig.getUrl() : "default");

		String prompt = buildMixSelectorPrompt(evidence, query, schemaDTO);
		log.debug("Built schema fine selection prompt as follows \n {} \n", prompt);

		Set<String> selectedTables = new HashSet<>();

		return FluxUtil.<ChatResponse, String>cascadeFlux(llmService.callUser(prompt), content -> {
			Flux<ChatResponse> nextFlux;
			if (sqlGenerateSchemaMissingAdvice != null) {
				log.debug("Adding tables from schema missing advice");
				nextFlux = this.fineSelect(schemaDTO, sqlGenerateSchemaMissingAdvice, selectedTables::addAll);
			}
			else {
				nextFlux = Flux.empty();
			}
			return nextFlux.doOnComplete(() -> {
				if (!content.trim().isEmpty()) {
					String jsonContent = MarkdownParserUtil.extractText(content);
					List<String> tableList;
					try {
						tableList = jsonParseUtil.tryConvertToObject(jsonContent, new TypeReference<List<String>>() {
						});
					}
					catch (Exception e) {
						// Some scenarios may prompt exceptions, such as:
						// java.lang.IllegalStateException:
						// Please provide database schema information so I can filter
						// relevant
						// tables based on your question.
						// TODO 目前异常接口直接返回500，未返回异常信息，后续优化将异常返回给用户
						log.error("Failed to parse fine selection response: {}", jsonContent, e);
						throw new IllegalStateException(jsonContent);
					}
					if (tableList != null && !tableList.isEmpty()) {
						selectedTables.addAll(tableList.stream().map(String::toLowerCase).collect(Collectors.toSet()));
						if (schemaDTO.getTable() != null) {
							int originalTableCount = schemaDTO.getTable().size();
							schemaDTO.getTable()
								.removeIf(table -> !selectedTables.contains(table.getName().toLowerCase()));
							int finalTableCount = schemaDTO.getTable().size();
							log.debug("Fine selection completed: {} -> {} tables, selected tables: {}",
									originalTableCount, finalTableCount, selectedTables);
						}
					}
				}
				dtoConsumer.accept(schemaDTO);
			});
		}, flux -> flux.map(ChatResponseUtil::getText)
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString));
	}

}
