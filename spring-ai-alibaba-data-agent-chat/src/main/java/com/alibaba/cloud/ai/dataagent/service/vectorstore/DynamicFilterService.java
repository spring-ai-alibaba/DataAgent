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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class DynamicFilterService {

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	public Filter.Expression buildDynamicFilter(String agentId, String vectorType) {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = new ArrayList<>();

		// 必须条件
		conditions.add(b.eq(Constant.AGENT_ID, agentId).build());
		conditions.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, vectorType).build());

		switch (vectorType) {

			case DocumentMetadataConstant.AGENT_KNOWLEDGE:
				// 场景 A: 知识库文档 -> 需要查 MySQL 获取启用状态
				List<Integer> validIds = agentKnowledgeMapper.selectRecalledKnowledgeIds(Integer.valueOf(agentId));

				if (validIds.isEmpty()) {
					log.warn("Agent {} has no recalled knowledge documents. Returning empty filter signal.", agentId);
					return null;
				}
				else {
					// 加入 ID 过滤
					conditions.add(b.in(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, validIds.toArray()).build());
				}
				break;

			case DocumentMetadataConstant.BUSINESS_TERM:
				// 场景 B: 业务知识 -> 查 business_knowledge 表的需要召回的
				List<Long> recalledBusinessKnowledgeIds = businessKnowledgeMapper
					.selectRecalledKnowledgeIds(Long.valueOf(agentId));

				if (recalledBusinessKnowledgeIds.isEmpty()) {
					log.warn("Agent {} has no recalled business terms. Returning empty filter signal.", agentId);
					return null;
				}
				else {
					// 添加 ID 过滤
					conditions
						.add(b.in(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, recalledBusinessKnowledgeIds.toArray())
							.build());
				}
				break;

			default:
				// 其他类型，默认只用 agentId + vectorType 过滤，不做额外处理
				log.debug("Using default filter for type: {}", vectorType);
				break;
		}

		// 组合所有条件
		return combineWithAnd(conditions);
	}

	/**
	 * 将多个过滤条件用 AND 连接起来
	 * @param conditions 条件列表
	 * @return 组合后的 Expression
	 */
	public static Filter.Expression combineWithAnd(List<Filter.Expression> conditions) {
		// 1. 判空
		if (conditions == null || conditions.isEmpty()) {
			return null;
		}

		// 2. 如果只有一个条件，直接返回，不用拼 AND
		if (conditions.size() == 1) {
			return conditions.get(0);
		}

		// 3. 核心逻辑：循环两两拼接
		// 初始结果是第一个条件
		Filter.Expression result = conditions.get(0);

		// 从第二个条件开始遍历
		for (int i = 1; i < conditions.size(); i++) {
			Filter.Expression nextCondition = conditions.get(i);

			// 手动创建 Expression 对象
			// 结构：(Result AND Next)
			result = new Filter.Expression(Filter.ExpressionType.AND, // 指定操作符
					result, // 左节点 (累加的结果)
					nextCondition // 右节点 (当前条件)
			);
		}

		return result;
	}

	public static String buildFilterExpressionString(Map<String, Object> filterMap) {
		if (filterMap == null || filterMap.isEmpty()) {
			return null;
		}

		// 验证键名是否合法（只包含字母、数字和下划线）
		for (String key : filterMap.keySet()) {
			if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
				throw new IllegalArgumentException("Invalid key name: " + key
						+ ". Keys must start with a letter or underscore and contain only alphanumeric characters and underscores.");
			}
		}

		return filterMap.entrySet().stream().map(entry -> {
			String key = entry.getKey();
			Object value = entry.getValue();

			// 处理空值
			if (value == null) {
				return key + " == null";
			}

			// 根据值的类型决定如何格式化
			if (value instanceof String) {
				// 转义字符串中的特殊字符
				String escapedValue = escapeStringLiteral((String) value);
				return key + " == '" + escapedValue + "'";
			}
			else if (value instanceof Number) {
				// 数字类型直接使用
				return key + " == " + value;
			}
			else if (value instanceof Boolean) {
				// 布尔值使用小写形式
				return key + " == " + ((Boolean) value).toString().toLowerCase();
			}
			else if (value instanceof Enum) {
				// 枚举类型，转换为字符串并转义
				String enumValue = ((Enum<?>) value).name();
				String escapedValue = escapeStringLiteral(enumValue);
				return key + " == '" + escapedValue + "'";
			}
			else {
				// 其他类型尝试转换为字符串并转义
				String stringValue = value.toString();
				String escapedValue = escapeStringLiteral(stringValue);
				return key + " == '" + escapedValue + "'";
			}
		}).collect(Collectors.joining(" && "));
	}

	/**
	 * 转义字符串字面量中的特殊字符
	 */
	public static String escapeStringLiteral(String input) {
		if (input == null) {
			return "";
		}

		// 转义反斜杠和单引号
		String escaped = input.replace("\\", "\\\\").replace("'", "\\'");

		// 转义其他特殊字符
		escaped = escaped.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t")
			.replace("\b", "\\b")
			.replace("\f", "\\f");

		return escaped;
	}

	public static Filter.Expression buildFilterExpressionForSearchTables(String agentId, List<String> tableNames) {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = new ArrayList<>();

		// 1. 基础条件：agentId
		conditions.add(b.eq(Constant.AGENT_ID, agentId).build());

		// 2. 基础条件：vectorType = TABLE
		conditions.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE).build());

		// 3. 动态条件：表名列表 IN 查询
		if (tableNames != null && !tableNames.isEmpty()) {
			conditions.add(b.in(DocumentMetadataConstant.NAME, tableNames.toArray()).build());
		}
		else {
			log.warn("Table names list is empty. Returning empty filter signal.");
			return null;
		}
		return combineWithAnd(conditions);
	}

	public Filter.Expression buildFilterExpressionForSearchColumns(String agentId, String upstreamTableName,
			List<String> columnNames) {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = new ArrayList<>();

		// 1. AgentId 条件
		conditions.add(b.eq(Constant.AGENT_ID, agentId).build());

		// 2. TableName 条件
		conditions.add(b.eq(DocumentMetadataConstant.TABLE_NAME, upstreamTableName).build());

		// 3. VectorType 条件
		conditions.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN).build());

		// 4. ColumnNames IN 条件
		if (columnNames != null && !columnNames.isEmpty()) {
			// metadata 中存储列名的字段叫name
			conditions.add(b.in(DocumentMetadataConstant.NAME, columnNames.toArray()).build());
		}
		else {
			log.warn("Column names list is empty. Returning empty filter signal.");
			return null;
		}
		return combineWithAnd(conditions);
	}

}
