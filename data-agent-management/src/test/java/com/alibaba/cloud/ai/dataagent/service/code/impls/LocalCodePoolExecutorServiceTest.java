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
package com.alibaba.cloud.ai.dataagent.service.code.impls;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class LocalCodePoolExecutorServiceTest {

	private static LocalCodePoolExecutorService service;

	private static boolean pythonAvailable = false;

	@BeforeAll
	static void init() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.setContainerNamePrefix("test-local-");
		properties.setCodeTimeout("60s");
		properties.setContainerTimeout(5L);
		try {
			service = new LocalCodePoolExecutorService(properties);
			pythonAvailable = true;
		}
		catch (IllegalStateException e) {
			pythonAvailable = false;
		}
	}

	static boolean isPythonAvailable() {
		return pythonAvailable;
	}

	private long callParseToMilliseconds(String timeString) throws Exception {
		Method method = LocalCodePoolExecutorService.class.getDeclaredMethod("parseToMilliseconds", String.class);
		method.setAccessible(true);
		return (long) method.invoke(service, timeString);
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_seconds_returns30000() throws Exception {
		assertEquals(30000, callParseToMilliseconds("30s"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_milliseconds_returns500() throws Exception {
		assertEquals(500, callParseToMilliseconds("500ms"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_minutes_returns120000() throws Exception {
		assertEquals(120000, callParseToMilliseconds("2m"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_hours_returns3600000() throws Exception {
		assertEquals(3600000, callParseToMilliseconds("1h"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_days_returns86400000() throws Exception {
		assertEquals(86400000, callParseToMilliseconds("1d"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_invalidFormat_returnsDefault60000() throws Exception {
		assertEquals(60000, callParseToMilliseconds("invalid"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_zeroSeconds_returnsZero() throws Exception {
		assertEquals(0, callParseToMilliseconds("0s"));
	}

	@Test
	@EnabledIf("isPythonAvailable")
	void parseToMilliseconds_largeValue_returnsCorrect() throws Exception {
		assertEquals(100000, callParseToMilliseconds("100s"));
	}

	@Test
	void constructor_noPythonOrWithPython_handledGracefully() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.setContainerNamePrefix("test-check-");
		properties.setCodeTimeout("60s");
		properties.setContainerTimeout(5L);

		try {
			LocalCodePoolExecutorService svc = new LocalCodePoolExecutorService(properties);
			assertNotNull(svc);
		}
		catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("Python"));
		}
	}

}
