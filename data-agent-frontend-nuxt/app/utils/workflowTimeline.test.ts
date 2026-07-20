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

import { describe, expect, it } from 'vitest';
import {
	TextType,
	type GraphNodeResponse,
} from '../services/graph/index';
import { groupWorkflowTimeline } from './workflowTimeline';

function event(
	nodeName: string,
	text: string,
	overrides: Partial<GraphNodeResponse> = {},
): GraphNodeResponse {
	return {
		agentId: '1',
		threadId: 'run-1',
		nodeName,
		textType: TextType.TEXT,
		text,
		error: false,
		complete: false,
		...overrides,
	};
}

describe('groupWorkflowTimeline', () => {
	it('preserves repeated node attempts as separate steps', () => {
		const groups = groupWorkflowTimeline([
			[event('SqlExecuteNode', 'first', { stepId: 'sql-1', attempt: 1 })],
			[event('SqlGenerateNode', 'retry', { stepId: 'generate-2', attempt: 2 })],
			[event('SqlExecuteNode', 'second', { stepId: 'sql-3', attempt: 2 })],
		]);

		expect(groups.map((group) => group.stepId)).toEqual([
			'sql-1',
			'generate-2',
			'sql-3',
		]);
		expect(groups[2]?.attempt).toBe(2);
	});

	it('uses the latest result payload from one step', () => {
		const table = event('SqlExecuteNode', '{"displayStyle":{"type":"table"}}', {
			stepId: 'sql-1',
			attempt: 1,
			textType: TextType.RESULT_SET,
		});
		const chart = event('SqlExecuteNode', '{"displayStyle":{"type":"bar"}}', {
			stepId: 'sql-1',
			attempt: 1,
			textType: TextType.RESULT_SET,
		});
		const groups = groupWorkflowTimeline([[table], [chart]]);
		const latest = [...(groups[0]?.items || [])]
			.reverse()
			.find((item) => item.textType === TextType.RESULT_SET);

		expect(groups).toHaveLength(1);
		expect(latest?.text).toContain('"bar"');
	});

	it('attaches a legacy result-only block to its preceding node', () => {
		const groups = groupWorkflowTimeline([
			[event('SqlExecuteNode', '执行SQL')],
			[
				event('SqlExecuteNode', '{"rows":1}', {
					textType: TextType.RESULT_SET,
				}),
			],
		]);

		expect(groups).toHaveLength(1);
		expect(groups[0]?.items).toHaveLength(2);
	});
});
