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

import {
	TextType,
	type GraphNodeResponse,
} from '../services/graph/index';

export interface WorkflowStepGroup {
	stepId: string;
	nodeName: string;
	attempt: number;
	items: GraphNodeResponse[];
}

/**
 * Groups workflow events by backend step identity. The fallback branch keeps persisted
 * timelines from older versions readable by attaching a result-only block to the
 * immediately preceding node execution.
 */
export function groupWorkflowTimeline(
	blocks: GraphNodeResponse[][],
): WorkflowStepGroup[] {
	const groups: WorkflowStepGroup[] = [];
	const groupsByStepId = new Map<string, WorkflowStepGroup>();
	const legacyAttempts = new Map<string, number>();

	for (const [index, block] of blocks.entries()) {
		const first = block[0];
		if (!first?.nodeName) continue;

		if (first.stepId) {
			let group = groupsByStepId.get(first.stepId);
			if (!group) {
				group = {
					stepId: first.stepId,
					nodeName: first.nodeName,
					attempt: first.attempt || 1,
					items: [],
				};
				groupsByStepId.set(first.stepId, group);
				groups.push(group);
			}
			group.items.push(...block);
			continue;
		}

		const previous = groups.at(-1);
		const resultOnly = block.every(
			(item) => item.textType === TextType.RESULT_SET,
		);
		if (resultOnly && previous?.nodeName === first.nodeName) {
			previous.items.push(...block);
			continue;
		}

		const attempt = (legacyAttempts.get(first.nodeName) || 0) + 1;
		legacyAttempts.set(first.nodeName, attempt);
		groups.push({
			stepId: `legacy-${first.nodeName}-${index}`,
			nodeName: first.nodeName,
			attempt,
			items: [...block],
		});
	}

	return groups;
}
