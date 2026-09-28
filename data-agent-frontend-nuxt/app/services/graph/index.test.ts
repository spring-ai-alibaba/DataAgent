/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import graphService, { TextType, type GraphNodeResponse } from './index';

class FakeEventSource {
	static readonly CLOSED = 2;

	static instances: FakeEventSource[] = [];

	readonly url: string;

	readyState = 1;

	onmessage: ((event: MessageEvent<string>) => Promise<void>) | null = null;

	onerror: ((event: Event) => Promise<void>) | null = null;

	private readonly listeners = new Map<
		string,
		Array<(event: MessageEvent<string> | Event) => Promise<void>>
	>();

	constructor(url: string) {
		this.url = url;
		FakeEventSource.instances.push(this);
	}

	addEventListener(
		type: string,
		listener: (event: MessageEvent<string> | Event) => Promise<void>,
	) {
		const listeners = this.listeners.get(type) || [];
		listeners.push(listener);
		this.listeners.set(type, listeners);
	}

	close = vi.fn(() => {
		this.readyState = FakeEventSource.CLOSED;
	});

	async emitMessage(response: GraphNodeResponse) {
		await this.onmessage?.({ data: JSON.stringify(response) } as MessageEvent<string>);
	}
}

describe('GraphService.streamSearch', () => {
	let fetchMock: ReturnType<typeof vi.fn>;

	beforeEach(() => {
		FakeEventSource.instances = [];
		fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200 });
		vi.stubGlobal('EventSource', FakeEventSource);
		vi.stubGlobal('fetch', fetchMock);
	});

	afterEach(() => {
		vi.unstubAllGlobals();
		vi.clearAllMocks();
	});

	it('carries turn and title state into SSE and includes agent ownership when stopping', async () => {
		const onMessage = vi.fn().mockResolvedValue(undefined);
		const close = await graphService.streamSearch(
			{
				agentId: '7',
				conversationId: 'conversation-1',
				threadId: 'run-before-resume',
				turnId: 'turn-1',
				query: 'continue',
				titleNeeded: true,
				humanFeedback: true,
				humanFeedbackContent: 'Accept',
				rejectedPlan: false,
				nl2sqlOnly: false,
			},
			onMessage,
		);

		const source = FakeEventSource.instances[0];
		expect(source).toBeDefined();
		const streamUrl = new URL(source!.url, 'http://localhost');
		expect(streamUrl.pathname).toBe('/api/stream/search');
		expect(streamUrl.searchParams.get('agentId')).toBe('7');
		expect(streamUrl.searchParams.get('conversationId')).toBe(
			'conversation-1',
		);
		expect(streamUrl.searchParams.get('threadId')).toBe('run-before-resume');
		expect(streamUrl.searchParams.get('turnId')).toBe('turn-1');
		expect(streamUrl.searchParams.get('titleNeeded')).toBe('true');

		await source!.emitMessage({
			agentId: '7',
			threadId: 'run-from-server',
			turnId: 'turn-1',
			nodeName: 'PlannerNode',
			textType: TextType.TEXT,
			text: 'planning',
			error: false,
			complete: false,
		});
		expect(onMessage).toHaveBeenCalledOnce();

		await close(true);

		expect(fetchMock).toHaveBeenCalledOnce();
		const [rawStopUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		const stopUrl = new URL(rawStopUrl, 'http://localhost');
		expect(stopUrl.pathname).toBe('/api/stream/stop');
		expect(stopUrl.searchParams.get('agentId')).toBe('7');
		expect(stopUrl.searchParams.get('conversationId')).toBe('conversation-1');
		expect(stopUrl.searchParams.get('threadId')).toBe('run-from-server');
		expect(init).toEqual({ method: 'POST', keepalive: true });
	});
});
