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

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Schema recall node that retrieves relevant database schema information based on
 * keywords and intent.
 *
 * This node is responsible for: - Recalling relevant tables based on user input -
 * Retrieving column documents based on extracted keywords - Organizing schema information
 * for subsequent processing - Providing streaming feedback during recall process
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class SchemaRecallNode implements NodeAction {

	private final SchemaService schemaService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// get input information
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class);
		String input = queryEnhanceOutputDTO.getCanonicalQuery();
		String agentId = StateUtil.getStringValue(state, AGENT_ID);

		// Execute business logic first - recall schema information immediately
		List<Document> tableDocuments = new ArrayList<>(schemaService.getTableDocumentsForAgent(agentId, input));
		// extract table names
		List<String> recalledTableNames = extractTableName(tableDocuments);
		List<Document> columnDocuments = schemaService.getColumnDocumentsByTableName(agentId, recalledTableNames);

		String failMessage = """
				\n 未检索到相关数据表

				这可能是因为：
				1. 数据源尚未初始化。
				2. 您的提问与当前数据库中的表结构无关。
				3. 请尝试点击“初始化数据源”或换一个与业务相关的问题。
				4. 如果你用A嵌入模型初始化数据源，却更换为B嵌入模型，请重新初始化数据源
				流程已终止。
				""";

		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始初步召回Schema信息..."));
			emitter.next(ChatResponseUtil.createResponse(
					"初步表信息召回完成，数量: " + tableDocuments.size() + "，表名: " + String.join(", ", recalledTableNames)));
			if (tableDocuments.isEmpty()) {
				emitter.next(ChatResponseUtil.createResponse(failMessage));
			}
			emitter.next(ChatResponseUtil.createResponse("初步Schema信息召回完成."));
			emitter.complete();
		});

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, currentState -> {
					return Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocuments,
							COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, columnDocuments);
				}, displayFlux);

		// Return the processing result
		return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
	}

	private static List<String> extractTableName(List<Document> tableDocuments) {
		List<String> tableNames = new ArrayList<>();
		// metadata中的name字段
		for (Document document : tableDocuments) {
			String name = (String) document.getMetadata().get("name");
			if (name != null && !name.isEmpty())
				tableNames.add(name);
		}
		log.info("At this SchemaRecallNode, Recall tables are: {}", tableNames);
		return tableNames;

	}

}
