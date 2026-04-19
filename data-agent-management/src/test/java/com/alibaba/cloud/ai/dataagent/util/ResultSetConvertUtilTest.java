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
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultSetConvertUtilTest {

	@Test
	void testConvert_singleRow() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(2);
		when(metaData.getColumnLabel(1)).thenReturn("id");
		when(metaData.getColumnLabel(2)).thenReturn("name");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString("id")).thenReturn("1");
		when(rs.getString("name")).thenReturn("Alice");

		List<String[]> result = ResultSetConvertUtil.convert(rs);

		assertEquals(2, result.size());
		assertArrayEquals(new String[] { "id", "name" }, result.get(0));
		assertArrayEquals(new String[] { "1", "Alice" }, result.get(1));
	}

	@Test
	void testConvert_emptyResultSet() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("col");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(false);

		List<String[]> result = ResultSetConvertUtil.convert(rs);
		assertEquals(1, result.size());
		assertArrayEquals(new String[] { "col" }, result.get(0));
	}

	@Test
	void testConvert_nullValues() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("col");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString("col")).thenReturn(null);

		List<String[]> result = ResultSetConvertUtil.convert(rs);
		assertEquals("", result.get(1)[0]);
	}

	@Test
	void testConvert_multipleRows() throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("id");

		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.next()).thenReturn(true, true, true, false);
		when(rs.getString("id")).thenReturn("1", "2", "3");

		List<String[]> result = ResultSetConvertUtil.convert(rs);
		assertEquals(4, result.size());
	}

}
