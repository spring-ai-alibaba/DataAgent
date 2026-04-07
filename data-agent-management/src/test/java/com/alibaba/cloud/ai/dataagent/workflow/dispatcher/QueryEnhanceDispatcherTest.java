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
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryEnhanceDispatcherTest {

	private QueryEnhanceDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new QueryEnhanceDispatcher();
	}

	@Test
	void apply_validQuery_routesToSchemaRecall() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户数据");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(SCHEMA_RECALL_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_emptyCanonicalQuery_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery("");
		dto.setExpandedQueries(List.of("query"));
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptyExpandedQueries_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery("valid query");
		dto.setExpandedQueries(new ArrayList<>());
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_nullCanonicalQuery_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery(null);
		dto.setExpandedQueries(List.of("query"));
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_missingOutput_throwsException() {
		OverAllState state = new OverAllState();

		assertThrows(IllegalStateException.class, () -> dispatcher.apply(state));
	}

}
