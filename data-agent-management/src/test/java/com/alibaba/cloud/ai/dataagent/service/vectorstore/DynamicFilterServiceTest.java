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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamicFilterServiceTest {

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Mock
	private BusinessKnowledgeMapper businessKnowledgeMapper;

	private DynamicFilterService dynamicFilterService;

	@BeforeEach
	void setUp() {
		dynamicFilterService = new DynamicFilterService(agentKnowledgeMapper, businessKnowledgeMapper);
	}

	@Test
	void buildDynamicFilter_agentKnowledge_withValidIds_returnsFilterExpression() {
		when(agentKnowledgeMapper.selectRecalledKnowledgeIds(anyInt())).thenReturn(List.of(1, 2, 3));

		Filter.Expression result = dynamicFilterService.buildDynamicFilter("1",
				DocumentMetadataConstant.AGENT_KNOWLEDGE);

		assertNotNull(result);
	}

	@Test
	void buildDynamicFilter_agentKnowledge_noValidIds_returnsNull() {
		when(agentKnowledgeMapper.selectRecalledKnowledgeIds(anyInt())).thenReturn(new ArrayList<>());

		Filter.Expression result = dynamicFilterService.buildDynamicFilter("1",
				DocumentMetadataConstant.AGENT_KNOWLEDGE);

		assertNull(result);
	}

	@Test
	void buildDynamicFilter_businessTerm_withValidIds_returnsFilterExpression() {
		when(businessKnowledgeMapper.selectRecalledKnowledgeIds(anyLong())).thenReturn(List.of(10L, 20L));

		Filter.Expression result = dynamicFilterService.buildDynamicFilter("1",
				DocumentMetadataConstant.BUSINESS_TERM);

		assertNotNull(result);
	}

	@Test
	void buildDynamicFilter_businessTerm_noValidIds_returnsNull() {
		when(businessKnowledgeMapper.selectRecalledKnowledgeIds(anyLong())).thenReturn(new ArrayList<>());

		Filter.Expression result = dynamicFilterService.buildDynamicFilter("1",
				DocumentMetadataConstant.BUSINESS_TERM);

		assertNull(result);
	}

	@Test
	void combineWithAnd_multipleConditions_returnsAndExpression() {
		Filter.Expression e1 = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("a"),
				new Filter.Value("1"));
		Filter.Expression e2 = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("b"),
				new Filter.Value("2"));
		Filter.Expression e3 = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("c"),
				new Filter.Value("3"));

		Filter.Expression result = DynamicFilterService.combineWithAnd(List.of(e1, e2, e3));

		assertNotNull(result);
		assertEquals(Filter.ExpressionType.AND, result.type());
	}

	@Test
	void combineWithAnd_singleCondition_returnsSingleExpression() {
		Filter.Expression e1 = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("a"),
				new Filter.Value("1"));

		Filter.Expression result = DynamicFilterService.combineWithAnd(List.of(e1));

		assertNotNull(result);
		assertEquals(Filter.ExpressionType.EQ, result.type());
	}

	@Test
	void combineWithAnd_emptyList_returnsNull() {
		Filter.Expression result = DynamicFilterService.combineWithAnd(new ArrayList<>());
		assertNull(result);
	}

	@Test
	void combineWithAnd_nullList_returnsNull() {
		Filter.Expression result = DynamicFilterService.combineWithAnd(null);
		assertNull(result);
	}

	@Test
	void escapeStringLiteral_withQuotes_escapesCorrectly() {
		String result = DynamicFilterService.escapeStringLiteral("it's a \"test\"");
		assertTrue(result.contains("\\'"));
	}

	@Test
	void escapeStringLiteral_noSpecialChars_returnsOriginal() {
		String result = DynamicFilterService.escapeStringLiteral("simple text");
		assertEquals("simple text", result);
	}

	@Test
	void escapeStringLiteral_null_returnsEmpty() {
		String result = DynamicFilterService.escapeStringLiteral(null);
		assertEquals("", result);
	}

	@Test
	void escapeStringLiteral_withNewlinesAndTabs_escapesCorrectly() {
		String result = DynamicFilterService.escapeStringLiteral("line1\nline2\ttab");
		assertEquals("line1\\nline2\\ttab", result);
	}

	@Test
	void buildFilterExpressionForSearchTables_validInput_returnsFilter() {
		Filter.Expression result = DynamicFilterService.buildFilterExpressionForSearchTables(1,
				List.of("users", "orders"));

		assertNotNull(result);
	}

	@Test
	void buildFilterExpressionForSearchTables_emptyTableNames_returnsNull() {
		Filter.Expression result = DynamicFilterService.buildFilterExpressionForSearchTables(1, new ArrayList<>());
		assertNull(result);
	}

	@Test
	void buildFilterExpressionForSearchTables_nullTableNames_returnsNull() {
		Filter.Expression result = DynamicFilterService.buildFilterExpressionForSearchTables(1, null);
		assertNull(result);
	}

	@Test
	void buildFilterExpressionForSearchColumns_validInput_returnsFilter() {
		Filter.Expression result = dynamicFilterService.buildFilterExpressionForSearchColumns(1,
				List.of("users", "orders"));

		assertNotNull(result);
	}

	@Test
	void buildFilterExpressionForSearchColumns_emptyList_returnsNull() {
		Filter.Expression result = dynamicFilterService.buildFilterExpressionForSearchColumns(1, new ArrayList<>());
		assertNull(result);
	}

	@Test
	void buildFilterExpressionForSearchColumns_nullList_returnsNull() {
		Filter.Expression result = dynamicFilterService.buildFilterExpressionForSearchColumns(1, null);
		assertNull(result);
	}

	@Test
	void buildFilterExpressionString_validMap_returnsExpression() {
		Map<String, Object> filterMap = new HashMap<>();
		filterMap.put("agentId", "1");
		filterMap.put("vectorType", "table");

		String result = DynamicFilterService.buildFilterExpressionString(filterMap);

		assertNotNull(result);
		assertTrue(result.contains("agentId == '1'"));
		assertTrue(result.contains("vectorType == 'table'"));
		assertTrue(result.contains("&&"));
	}

	@Test
	void buildFilterExpressionString_emptyMap_returnsNull() {
		String result = DynamicFilterService.buildFilterExpressionString(new HashMap<>());
		assertNull(result);
	}

	@Test
	void buildFilterExpressionString_nullMap_returnsNull() {
		String result = DynamicFilterService.buildFilterExpressionString(null);
		assertNull(result);
	}

	@Test
	void buildFilterExpressionString_withNumberAndBoolean_formatsCorrectly() {
		Map<String, Object> filterMap = new HashMap<>();
		filterMap.put("count", 42);
		filterMap.put("active", true);

		String result = DynamicFilterService.buildFilterExpressionString(filterMap);

		assertNotNull(result);
		assertTrue(result.contains("count == 42"));
		assertTrue(result.contains("active == true"));
	}

	@Test
	void buildFilterExpressionString_invalidKey_throwsException() {
		Map<String, Object> filterMap = new HashMap<>();
		filterMap.put("invalid-key", "value");

		assertThrows(IllegalArgumentException.class, () -> DynamicFilterService.buildFilterExpressionString(filterMap));
	}

}
