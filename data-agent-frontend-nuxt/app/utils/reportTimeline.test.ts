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
import { TextType, type GraphNodeResponse } from '../services/graph/index';
import { applyReportContent, extractReportContent } from './reportTimeline';

function reportEvent(
	text: string,
	textType: TextType = TextType.TEXT,
): GraphNodeResponse {
	return {
		agentId: '1',
		threadId: 'run-1',
		stepId: 'ReportGeneratorNode-11',
		attempt: 1,
		nodeName: 'ReportGeneratorNode',
		textType,
		text,
		error: false,
		complete: false,
	};
}

describe('report timeline', () => {
	it('promotes the report summary event to markdown while aggregating chunks', () => {
		const block = [reportEvent('开始生成报告...')];

		applyReportContent(block, '# 已完成订单报告', TextType.MARK_DOWN);

		expect(block[0]?.text).toBe('# 已完成订单报告');
		expect(block[0]?.textType).toBe(TextType.MARK_DOWN);
	});

	it('extracts reports persisted before the text type fix', () => {
		const timeline = JSON.stringify([
			[reportEvent('# 历史数据分析报告', TextType.TEXT)],
		]);

		expect(extractReportContent(timeline)).toBe('# 历史数据分析报告');
	});

	it('does not mistake report lifecycle messages for report content', () => {
		for (const status of ['开始生成报告...', '报告生成完成！']) {
			const timeline = JSON.stringify([[reportEvent(status, TextType.TEXT)]]);
			expect(extractReportContent(timeline)).toBeNull();
		}
	});

	it('extracts a correctly typed markdown report', () => {
		const timeline = JSON.stringify([
			[reportEvent('# 新数据分析报告', TextType.MARK_DOWN)],
		]);

		expect(extractReportContent(timeline)).toBe('# 新数据分析报告');
	});
});
