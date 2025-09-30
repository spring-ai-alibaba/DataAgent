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
package com.alibaba.cloud.ai.util;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for processing database schema information. Provides common schema
 * processing functionality for vector store operations.
 */
public class SchemaProcessorUtil {

	/**
	 * Processes table information by fetching column details and setting metadata.
	 * @param tableInfoBO the table to process
	 * @param dqp database query parameter
	 * @param dbConfig database configuration
	 * @param dbAccessor database accessor
	 * @param objectMapper object mapper for JSON serialization
	 * @throws Exception if processing fails
	 */
	public static void enrichTableMetadata(TableInfoBO tableInfoBO, DbQueryParameter dqp, DbConfig dbConfig,
			Accessor dbAccessor, ObjectMapper objectMapper, Map<String, List<String>> foreignKeyMap) throws Exception {
		dqp.setTable(tableInfoBO.getName());
		List<ColumnInfoBO> columnInfoBOS = dbAccessor.showColumns(dbConfig, dqp);

		for (ColumnInfoBO columnInfoBO : columnInfoBOS) {
			dqp.setColumn(columnInfoBO.getName());
			List<String> sampleColumnValue = dbAccessor.sampleColumn(dbConfig, dqp);
			sampleColumnValue = Optional.ofNullable(sampleColumnValue)
				.orElse(new ArrayList<>())
				.stream()
				.filter(Objects::nonNull)
				.distinct()
				.limit(3)
				.filter(s -> s.length() <= 100)
				.toList();

			columnInfoBO.setTableName(tableInfoBO.getName());
			try {
				columnInfoBO.setSamples(objectMapper.writeValueAsString(sampleColumnValue));
			}
			catch (JsonProcessingException e) {
				columnInfoBO.setSamples("[]");
			}
		}

		// 保存处理过的列数据到TableInfoBO，供后续使用
		tableInfoBO.setColumns(columnInfoBOS);

		List<ColumnInfoBO> targetPrimaryList = columnInfoBOS.stream()
			.filter(ColumnInfoBO::isPrimary)
			.collect(Collectors.toList());

		if (CollectionUtils.isNotEmpty(targetPrimaryList)) {
			List<String> columnNames = targetPrimaryList.stream()
				.map(ColumnInfoBO::getName)
				.collect(Collectors.toList());
			tableInfoBO.setPrimaryKeys(columnNames);
		}
		else {
			tableInfoBO.setPrimaryKeys(new ArrayList<>());
		}

		tableInfoBO
			.setForeignKey(String.join("、", foreignKeyMap.getOrDefault(tableInfoBO.getName(), new ArrayList<>())));
	}

	/**
	 * Private constructor to prevent instantiation.
	 */
	private SchemaProcessorUtil() {
		throw new AssertionError("Cannot instantiate utility class");
	}

}
