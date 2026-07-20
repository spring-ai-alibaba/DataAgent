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
package com.alibaba.cloud.ai.dataagent.service.lineage;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineageQueryServiceTest {

	@Mock
	private LineageMetadataService metadataService;

	@Mock
	private Accessor accessor;

	private LineageQueryService service;

	private TableLineageMetadata paintMetadata;

	private TableLineageMetadata reportMetadata;

	@BeforeEach
	void setUp() {
		service = new LineageQueryService(metadataService, new DataAgentProperties());
		paintMetadata = new TableLineageMetadata("paint_product_shift_stats", "paint_product_shift_stats", null,
				null, "source_file_name", "source_file_sha256", "source_sheet", "created_at");
		reportMetadata = new TableLineageMetadata("inspection_report", "inspection_report", null, null,
				"source_file_name", "source_file_sha256", "source_sheet", "created_at");
	}

	@Test
	void buildLineageQueries_directTableKeepsWhereAndRemovesAggregation() {
		DatasourceLineageMetadata metadata = metadata(paintMetadata);
		String sql = "SELECT p.stat_date, SUM(p.ng_total) FROM paint_product_shift_stats p "
				+ "WHERE p.stat_date >= '2026-07-01' GROUP BY p.stat_date ORDER BY p.stat_date";

		List<String> queries = service.buildLineageQueries(sql, metadata);

		assertEquals(1, queries.size());
		String lineageSql = queries.get(0);
		assertTrue(lineageSql.contains("`p`.`source_file_name`"));
		assertTrue(lineageSql.contains("WHERE p.stat_date >= '2026-07-01'"));
		assertFalse(lineageSql.toLowerCase().contains("group by"));
		assertFalse(lineageSql.toLowerCase().contains("order by"));
	}

	@Test
	void buildLineageQueries_inheritedTableAddsSourceJoin() {
		TableLineageMetadata measurement = new TableLineageMetadata("dimension_measurement", "inspection_report",
				"report_id", "id", "source_file_name", "source_file_sha256", "source_sheet", "created_at");
		DatasourceLineageMetadata metadata = metadata(reportMetadata, measurement);

		List<String> queries = service.buildLineageQueries(
				"SELECT m.value FROM dimension_measurement m WHERE m.measurement_point = 'P1'", metadata);

		assertEquals(1, queries.size());
		assertTrue(queries.get(0).contains("JOIN `inspection_report` `__lineage_source`"));
		assertTrue(queries.get(0).contains("`m`.`report_id` = `__lineage_source`.`id`"));
		assertTrue(queries.get(0).contains("WHERE m.measurement_point = 'P1'"));
	}

	@Test
	void querySources_deduplicatesByHashAndSheet() throws Exception {
		DatasourceLineageMetadata metadata = metadata(paintMetadata);
		when(metadataService.getMetadata(4)).thenReturn(metadata);
		Map<String, String> source = Map.of("source_table", "paint_product_shift_stats", "source_file_name",
				"paint.xlsx", "source_file_sha256", "abcdef", "source_sheet", "Sheet1", "source_imported_at",
				"2026-07-16 10:00:00");
		ResultSetBO result = ResultSetBO.builder().data(List.of(source, source)).build();
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(result);
		DbConfigBO dbConfig = DbConfigBO.builder().schema("quality").dialectType("mysql").build();

		List<Map<String, String>> sources = service.querySources(4, dbConfig, accessor,
				"SELECT p.ng_total FROM paint_product_shift_stats p WHERE p.stat_date = '2026-07-16'");

		assertEquals(1, sources.size());
		assertEquals("paint.xlsx", sources.get(0).get("source_file_name"));
	}

	@Test
	void buildLineageQueries_havingQuerySkipsInsteadOfReturningInaccurateSources() {
		List<String> queries = service.buildLineageQueries(
				"SELECT product_name, SUM(ng_total) total FROM paint_product_shift_stats GROUP BY product_name HAVING total > 10",
				metadata(paintMetadata));

		assertTrue(queries.isEmpty());
	}

	private DatasourceLineageMetadata metadata(TableLineageMetadata... tables) {
		Map<String, TableLineageMetadata> map = new LinkedHashMap<>();
		for (TableLineageMetadata table : tables) {
			map.put(table.tableName().toLowerCase(), table);
		}
		return new DatasourceLineageMetadata(4, map);
	}

}
