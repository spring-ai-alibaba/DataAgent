/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.connector;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultSetBuilderTest {

	@Test
	void testBuildFrom_singleRow() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(2);
		when(metaData.getColumnLabel(1)).thenReturn("id");
		when(metaData.getColumnLabel(2)).thenReturn("name");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString("id")).thenReturn("1");
		when(rs.getString("name")).thenReturn("Alice");

		ResultSetBO result = ResultSetBuilder.buildFrom(rs, "public");

		assertNotNull(result);
		assertEquals(2, result.getColumn().size());
		assertTrue(result.getColumn().contains("id"));
		assertTrue(result.getColumn().contains("name"));
		assertEquals(1, result.getData().size());
		assertEquals("1", result.getData().get(0).get("id"));
		assertEquals("Alice", result.getData().get(0).get("name"));
	}

	@Test
	void testBuildFrom_emptyResultSet() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("id");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(false);

		ResultSetBO result = ResultSetBuilder.buildFrom(rs, "public");

		assertNotNull(result);
		assertEquals(1, result.getColumn().size());
		assertTrue(result.getData().isEmpty());
	}

	@Test
	void testBuildFrom_nullValue() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("col");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString("col")).thenReturn(null);

		ResultSetBO result = ResultSetBuilder.buildFrom(rs, "public");

		assertEquals("", result.getData().get(0).get("col"));
	}

	@Test
	void testBuildFrom_cleansBackticks() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("`my_col`");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString("`my_col`")).thenReturn("value");

		ResultSetBO result = ResultSetBuilder.buildFrom(rs, "public");

		assertTrue(result.getColumn().contains("my_col"));
		assertEquals("value", result.getData().get(0).get("my_col"));
	}

	@Test
	void testBuildFrom_cleansDoubleQuotes() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("\"my_col\"");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString("\"my_col\"")).thenReturn("value");

		ResultSetBO result = ResultSetBuilder.buildFrom(rs, "public");

		assertTrue(result.getColumn().contains("my_col"));
	}

}
