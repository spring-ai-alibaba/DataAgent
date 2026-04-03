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

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTemplateUtilTest {

	@Mock
	private DataAgentProperties dataAgentProperties;

	@Mock
	private DataAgentProperties.ReportTemplate reportTemplateConfig;

	private ReportTemplateUtil util;

	private void initUtil() {
		when(dataAgentProperties.getReportTemplate()).thenReturn(reportTemplateConfig);
		when(reportTemplateConfig.getMarkedUrl()).thenReturn("https://cdn.example.com/marked.min.js");
		when(reportTemplateConfig.getEchartsUrl()).thenReturn("https://cdn.example.com/echarts.min.js");
		util = new ReportTemplateUtil(dataAgentProperties);
	}

	@Test
	void testGetHeader_containsCdnUrls() {
		initUtil();
		String header = util.getHeader();
		assertTrue(header.contains("https://cdn.example.com/marked.min.js"));
		assertTrue(header.contains("https://cdn.example.com/echarts.min.js"));
	}

	@Test
	void testGetHeader_containsHtmlStructure() {
		initUtil();
		String header = util.getHeader();
		assertTrue(header.contains("<!DOCTYPE html>"));
		assertTrue(header.contains("<head>"));
		assertTrue(header.contains("raw-markdown"));
	}

	@Test
	void testGetFooter_containsScript() {
		ReportTemplateUtil footerUtil = new ReportTemplateUtil(dataAgentProperties);
		String footer = footerUtil.getFooter();
		assertTrue(footer.contains("</html>"));
		assertTrue(footer.contains("window.onload"));
		assertTrue(footer.contains("echarts"));
	}

	@Test
	void testCleanJsonExample_notNull() {
		assertNotNull(ReportTemplateUtil.cleanJsonExample);
		assertTrue(ReportTemplateUtil.cleanJsonExample.contains("title"));
		assertTrue(ReportTemplateUtil.cleanJsonExample.contains("series"));
	}

}
