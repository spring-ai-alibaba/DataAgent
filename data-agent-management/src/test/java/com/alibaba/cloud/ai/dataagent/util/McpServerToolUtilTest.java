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

import com.alibaba.cloud.ai.dataagent.annotation.McpServerTool;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpServerToolUtilTest {

	@Test
	void testExcludeMcpServerTool_filtersAnnotatedBeans() {
		GenericApplicationContext context = mock(GenericApplicationContext.class);

		when(context.getBeanNamesForType(Runnable.class)).thenReturn(new String[] { "bean1", "bean2", "bean3" });
		when(context.getBeanNamesForAnnotation(McpServerTool.class)).thenReturn(new String[] { "bean2" });

		Runnable r1 = mock(Runnable.class);
		Runnable r3 = mock(Runnable.class);
		when(context.getBean("bean1", Runnable.class)).thenReturn(r1);
		when(context.getBean("bean3", Runnable.class)).thenReturn(r3);

		List<Runnable> result = McpServerToolUtil.excludeMcpServerTool(context, Runnable.class);

		assertEquals(2, result.size());
		assertTrue(result.contains(r1));
		assertTrue(result.contains(r3));
	}

	@Test
	void testExcludeMcpServerTool_noAnnotatedBeans() {
		GenericApplicationContext context = mock(GenericApplicationContext.class);

		when(context.getBeanNamesForType(Runnable.class)).thenReturn(new String[] { "bean1" });
		when(context.getBeanNamesForAnnotation(McpServerTool.class)).thenReturn(new String[0]);

		Runnable r1 = mock(Runnable.class);
		when(context.getBean("bean1", Runnable.class)).thenReturn(r1);

		List<Runnable> result = McpServerToolUtil.excludeMcpServerTool(context, Runnable.class);
		assertEquals(1, result.size());
	}

	@Test
	void testExcludeMcpServerTool_allExcluded() {
		GenericApplicationContext context = mock(GenericApplicationContext.class);

		when(context.getBeanNamesForType(Runnable.class)).thenReturn(new String[] { "bean1" });
		when(context.getBeanNamesForAnnotation(McpServerTool.class)).thenReturn(new String[] { "bean1" });

		List<Runnable> result = McpServerToolUtil.excludeMcpServerTool(context, Runnable.class);
		assertTrue(result.isEmpty());
	}

}
