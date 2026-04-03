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
package com.alibaba.cloud.ai.dataagent.service.graph.Context;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamContextTest {

	@Test
	void testAppendAndGetCollectedOutput() {
		StreamContext ctx = new StreamContext();
		ctx.appendOutput("Hello ");
		ctx.appendOutput("World");
		assertEquals("Hello World", ctx.getCollectedOutput());
	}

	@Test
	void testCleanup_onlyRunsOnce() {
		StreamContext ctx = new StreamContext();
		Disposable disposable = mock(Disposable.class);
		when(disposable.isDisposed()).thenReturn(false);
		ctx.setDisposable(disposable);

		@SuppressWarnings("unchecked")
		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = mock(Sinks.Many.class);
		ctx.setSink(sink);

		ctx.cleanup();
		assertTrue(ctx.isCleaned());
		verify(disposable).dispose();
		verify(sink).tryEmitComplete();

		// Second call should be no-op
		ctx.cleanup();
		verify(disposable, times(1)).dispose();
	}

	@Test
	void testCleanup_nullDisposableAndSink() {
		StreamContext ctx = new StreamContext();
		assertDoesNotThrow(ctx::cleanup);
		assertTrue(ctx.isCleaned());
	}

	@Test
	void testCleanup_alreadyDisposed() {
		StreamContext ctx = new StreamContext();
		Disposable disposable = mock(Disposable.class);
		when(disposable.isDisposed()).thenReturn(true);
		ctx.setDisposable(disposable);

		ctx.cleanup();
		verify(disposable, never()).dispose();
	}

	@Test
	void testIsCleanedInitiallyFalse() {
		StreamContext ctx = new StreamContext();
		assertFalse(ctx.isCleaned());
	}

	@Test
	void testTextType() {
		StreamContext ctx = new StreamContext();
		ctx.setTextType(TextType.TEXT);
		assertEquals(TextType.TEXT, ctx.getTextType());
	}

}
