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

import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeBeanUtilTest {

	@Mock
	private ApplicationContext context;

	private NodeBeanUtil nodeBeanUtil;

	@BeforeEach
	void setUp() {
		nodeBeanUtil = new NodeBeanUtil(context);
	}

	@Test
	void testGetNodeBean() {
		TestNodeAction expected = new TestNodeAction();
		when(context.getBean(TestNodeAction.class)).thenReturn(expected);

		NodeAction result = nodeBeanUtil.getNodeBean(TestNodeAction.class);
		assertSame(expected, result);
	}

	@Test
	void testGetNodeBeanAsync() {
		TestNodeAction expected = new TestNodeAction();
		when(context.getBean(TestNodeAction.class)).thenReturn(expected);

		assertNotNull(nodeBeanUtil.getNodeBeanAsync(TestNodeAction.class));
	}

	@Test
	void testGetEdgeBean() {
		TestEdgeAction expected = new TestEdgeAction();
		when(context.getBean(TestEdgeAction.class)).thenReturn(expected);

		EdgeAction result = nodeBeanUtil.getEdgeBean(TestEdgeAction.class);
		assertSame(expected, result);
	}

	@Test
	void testGetEdgeBeanAsync() {
		TestEdgeAction expected = new TestEdgeAction();
		when(context.getBean(TestEdgeAction.class)).thenReturn(expected);

		assertNotNull(nodeBeanUtil.getEdgeBeanAsync(TestEdgeAction.class));
	}

	static class TestNodeAction implements NodeAction {

		@Override
		public java.util.Map<String, Object> apply(com.alibaba.cloud.ai.graph.OverAllState state) {
			return java.util.Map.of();
		}

	}

	static class TestEdgeAction implements EdgeAction {

		@Override
		public String apply(com.alibaba.cloud.ai.graph.OverAllState state) {
			return "";
		}

	}

}
