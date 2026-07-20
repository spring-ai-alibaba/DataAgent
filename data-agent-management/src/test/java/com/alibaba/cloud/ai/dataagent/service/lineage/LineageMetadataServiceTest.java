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
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineageMetadataServiceTest {

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private Accessor accessor;

	private LineageMetadataService service;

	@BeforeEach
	void setUp() throws Exception {
		DataAgentProperties properties = new DataAgentProperties();
		service = new LineageMetadataService(datasourceService, accessorFactory, properties);

		Datasource datasource = Datasource.builder().id(4).type("mysql").build();
		DbConfigBO dbConfig = DbConfigBO.builder().schema("quality").dialectType("mysql").build();
		when(datasourceService.getDatasourceById(4)).thenReturn(datasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(dbConfig);
		when(datasourceService.getDatasourceTables(4))
			.thenReturn(List.of("xlsx_report_lineage", "inspection_report", "dimension_measurement", "plain_table"));
		when(datasourceService.getTableColumns(4, "xlsx_report_lineage"))
			.thenReturn(List.of("id", "source_file_name", "source_file_sha256", "source_sheet", "imported_at"));
		when(datasourceService.getTableColumns(4, "inspection_report"))
			.thenReturn(List.of("id", "source_report_id", "title"));
		when(datasourceService.getTableColumns(4, "dimension_measurement"))
			.thenReturn(List.of("id", "source_report_id", "report_id", "value"));
		when(datasourceService.getTableColumns(4, "plain_table")).thenReturn(List.of("id", "name"));
		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showForeignKeys(any(), any())).thenReturn(List.of(
				ForeignKeyInfoBO.builder()
					.table("inspection_report")
					.column("source_report_id")
					.referencedTable("xlsx_report_lineage")
					.referencedColumn("id")
					.build(),
				ForeignKeyInfoBO.builder()
					.table("dimension_measurement")
					.column("source_report_id")
					.referencedTable("xlsx_report_lineage")
					.referencedColumn("id")
					.build()));
	}

	@Test
	void getMetadata_detectsDirectAndForeignKeyInheritedTables() {
		DatasourceLineageMetadata metadata = service.getMetadata(4);

		assertEquals(3, metadata.tables().size());
		assertTrue(metadata.tables().get("xlsx_report_lineage").direct());
		assertEquals("xlsx_report_lineage", metadata.tables().get("inspection_report").sourceTableName());
		TableLineageMetadata measurement = metadata.tables().get("dimension_measurement");
		assertFalse(measurement.direct());
		assertEquals("xlsx_report_lineage", measurement.sourceTableName());
		assertEquals("source_report_id", measurement.localJoinColumn());
		assertEquals("id", measurement.sourceJoinColumn());
	}

	@Test
	void getMetadata_usesCacheUntilInvalidated() throws Exception {
		service.getMetadata(4);
		service.getMetadata(4);
		verify(datasourceService).getDatasourceTables(4);

		service.invalidate(4);
		service.getMetadata(4);
		verify(datasourceService, times(2)).getDatasourceTables(4);
	}

}
