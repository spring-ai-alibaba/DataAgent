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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseUtilTest {

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private DatasourceService datasourceService;

	@InjectMocks
	private DatabaseUtil databaseUtil;

	@Test
	void getDatasourceDbConfig_usesPinnedDatasourceId() {
		Datasource datasource = new Datasource();

		DbConfigBO expectedConfig = new DbConfigBO();
		expectedConfig.setUrl("jdbc:mysql://localhost:3306/test");
		expectedConfig.setSchema("test");
		expectedConfig.setDialectType("mysql");

		when(datasourceService.getDatasourceById(3)).thenReturn(datasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(expectedConfig);

		DbConfigBO result = databaseUtil.getDatasourceDbConfig(3);

		assertNotNull(result);
		assertEquals("jdbc:mysql://localhost:3306/test", result.getUrl());
		verify(datasourceService).getDatasourceById(3);
		verify(datasourceService).getDbConfig(datasource);
	}

	@Test
	void getDatasourceDbConfig_missingDatasourceFailsClosed() {
		when(datasourceService.getDatasourceById(3)).thenReturn(null);

		assertThrows(IllegalStateException.class, () -> databaseUtil.getDatasourceDbConfig(3));
		verify(datasourceService, never()).getDbConfig(any());
	}

	@Test
	void getAccessor_usesProvidedPinnedConfiguration() {
		DbConfigBO config = new DbConfigBO();
		Accessor mockAccessor = mock(Accessor.class);
		when(accessorFactory.getAccessorByDbConfig(config)).thenReturn(mockAccessor);

		Accessor result = databaseUtil.getAccessor(config);

		assertNotNull(result);
		assertSame(mockAccessor, result);
	}

}
