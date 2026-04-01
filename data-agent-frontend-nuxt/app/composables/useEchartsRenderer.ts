import { nextTick, onBeforeUnmount } from 'vue';
import * as echarts from 'echarts';

const EXTENDED_COLORS = [
	'#5584FF', '#36CBCB', '#4ECB74', '#FAD337', '#F2637B', '#975FEE',
	'#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272',
	'#fc8452', '#9a60b4', '#ea7ccc',
	'#0082fc', '#fdd845', '#22ed7c', '#1d27c9', '#05f8d6', '#f9e264',
	'#f47a75', '#009db2',
];

let debounceTimer: ReturnType<typeof setTimeout> | null = null;

function renderEChartsInContainer(container: HTMLElement | null) {
	if (!container) return;

	if (debounceTimer) clearTimeout(debounceTimer);
	debounceTimer = setTimeout(() => {
		const elements = container.querySelectorAll<HTMLElement>('.md-echarts');
		elements.forEach((el) => {
			try {
				const content = el.textContent;
				if (!content || content.trim() === '') return;

				const hasValidJson =
					/\{[\s\S]*\}/.test(content) &&
					content.match(/\{/g)?.length === content.match(/\}/g)?.length;

				if (!hasValidJson) return;

				const options = JSON.parse(content);
				if (!options.color) {
					options.color = EXTENDED_COLORS;
				}

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
	}, 300);
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
	const chartContainers: HTMLElement[] = [];

	function renderECharts(container: HTMLElement | null) {
		if (!container) return;
		if (!chartContainers.includes(container)) chartContainers.push(container);
		nextTick(() => renderEChartsInContainer(container));
	}

	onBeforeUnmount(() => {
		if (debounceTimer) clearTimeout(debounceTimer);
		chartContainers.forEach((c) => disposeEChartsInContainer(c));
	});

	return { renderECharts, disposeEChartsInContainer };
}
