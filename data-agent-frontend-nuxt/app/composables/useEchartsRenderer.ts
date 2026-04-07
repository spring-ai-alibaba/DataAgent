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

import { nextTick, onBeforeUnmount } from 'vue';
import * as echarts from 'echarts';

const EXTENDED_COLORS = [
	'#5584FF',
	'#36CBCB',
	'#4ECB74',
	'#FAD337',
	'#F2637B',
	'#975FEE',
	'#5470c6',
	'#91cc75',
	'#fac858',
	'#ee6666',
	'#73c0de',
	'#3ba272',
	'#fc8452',
	'#9a60b4',
	'#ea7ccc',
	'#0082fc',
	'#fdd845',
	'#22ed7c',
	'#1d27c9',
	'#05f8d6',
	'#f9e264',
	'#f47a75',
	'#009db2',
];

function renderEChartsInContainer(container: HTMLElement) {
	const elements = container.querySelectorAll<HTMLElement>('.md-echarts');
	elements.forEach((el) => {
		try {
			const rawConfig = el.getAttribute('data-echarts-config');
			if (!rawConfig) return; // already rendered (attribute removed after init)

			const code = rawConfig
				.replace(/&quot;/g, '"')
				.replace(/&lt;/g, '<')
				.replace(/&gt;/g, '>')
				.replace(/&amp;/g, '&');

			if (!code || code.trim() === '') return;

			const options = new Function(`return (${code})`)() as Record<
				string,
				unknown
			>;
			if (!options.color) {
				options.color = EXTENDED_COLORS;
			}

			// Mark as initialized before touching DOM
			el.removeAttribute('data-echarts-config');
			el.textContent = '';

			const existingChart = echarts.getInstanceByDom(el);
			if (existingChart) {
				existingChart.setOption(options, true);
			} else {
				const chart = echarts.init(el);
				chart.setOption(options);
			}
		} catch (e) {
			console.error('ECharts rendering error:', e);
		}
	});
}

function disposeEChartsInContainer(container: HTMLElement | null) {
	if (!container) return;
	const elements = container.querySelectorAll<HTMLElement>('.md-echarts');
	elements.forEach((el) => {
		const chart = echarts.getInstanceByDom(el);
		if (chart) chart.dispose();
	});
}

export function useEchartsRenderer() {
	// Each composable instance has its own timer — no cross-instance interference
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	const chartContainers: HTMLElement[] = [];

	function renderECharts(container: HTMLElement | null) {
		if (!container) return;
		if (!chartContainers.includes(container)) chartContainers.push(container);

		// Cancel any pending debounce for this instance only
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(() => {
			debounceTimer = null;
			nextTick(() => renderEChartsInContainer(container));
		}, 200);
	}

	onBeforeUnmount(() => {
		if (debounceTimer) clearTimeout(debounceTimer);
		chartContainers.forEach((c) => disposeEChartsInContainer(c));
	});

	return { renderECharts, disposeEChartsInContainer };
}
