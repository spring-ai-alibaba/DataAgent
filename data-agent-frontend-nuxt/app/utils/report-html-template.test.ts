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
import { buildReportHtml } from './report-html-template';

describe('buildReportHtml', () => {
	it('recognizes common ECharts fence language aliases', () => {
		const html = buildReportHtml('```javascript\n{"series":[]}\n```');

		expect(html).toContain("'echarts', 'json', 'javascript', 'js'");
		expect(html).toContain('toLowerCase()');
	});

	it('reserves separate space for chart titles and legends', () => {
		const html = buildReportHtml('```echarts\n{"title":{"text":"趋势"},"legend":{},"series":[]}\n```');

		expect(html).toContain('normalizeChartLayout');
		expect(html).toContain('legend.top = titles.length > 0 ? 50 : 10');
		expect(html).toContain('grid.containLabel = true');
	});
});
