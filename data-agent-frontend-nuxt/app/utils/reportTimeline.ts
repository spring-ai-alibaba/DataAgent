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

import { TextType, type GraphNodeResponse } from '../services/graph/index';

const REPORT_NODE_NAME = 'ReportGeneratorNode';
const REPORT_LIFECYCLE_MESSAGES = new Set([
	'开始生成报告...',
	'报告生成完成！',
]);
const HTML_REPORT_PROGRESS_PREFIX = '正在收集HTML报告...';

export function applyReportContent(
	block: GraphNodeResponse[] | undefined,
	content: string,
	textType: TextType,
): void {
	const reportEvent = block?.[0];
	if (!reportEvent) return;
	reportEvent.text = content;
	reportEvent.textType = textType;
}

export function extractReportContent(timelineJson: string): string | null {
	try {
		const blocks = JSON.parse(timelineJson) as GraphNodeResponse[][];
		for (const block of blocks) {
			const reportEvent = block[0];
			if (reportEvent?.nodeName !== REPORT_NODE_NAME || !reportEvent.text)
				continue;
			if (reportEvent.textType === TextType.MARK_DOWN) return reportEvent.text;

			const legacyText = reportEvent.text.trim();
			if (
				reportEvent.textType === TextType.TEXT &&
				!REPORT_LIFECYCLE_MESSAGES.has(legacyText) &&
				!legacyText.startsWith(HTML_REPORT_PROGRESS_PREFIX)
			)
				return reportEvent.text;
		}
	} catch {
		/* ignore malformed historical messages */
	}
	return null;
}
