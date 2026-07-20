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
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
	DatasetComponent,
	GridComponent,
	LegendComponent,
	TitleComponent,
	TooltipComponent,
} from 'echarts/components';
import { LabelLayout } from 'echarts/features';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
	BarChart,
	LineChart,
	PieChart,
	DatasetComponent,
	GridComponent,
	LegendComponent,
	TitleComponent,
	TooltipComponent,
	LabelLayout,
	CanvasRenderer,
]);

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

interface ContainerController {
	container: HTMLElement;
	mutationObserver: MutationObserver;
	resizeObserver: ResizeObserver;
	observedElements: Set<HTMLElement>;
	renderRafId: number | null;
}

function decodeChartConfig(rawConfig: string): string {
	return rawConfig
		.replace(/&quot;/g, '"')
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&amp;/g, '&');
}

function showChartError(element: HTMLElement, error: unknown) {
	const message = error instanceof Error ? error.message : String(error);
	element.dataset.echartsRenderError = message;
	element.textContent = '';

	const errorBox = document.createElement('div');
	errorBox.className = 'md-echarts-error';
	errorBox.style.cssText = [
		'display:flex',
		'align-items:center',
		'justify-content:center',
		'height:100%',
		'min-height:160px',
		'padding:16px',
		'border:1px dashed #ef4444',
		'border-radius:6px',
		'background:#fef2f2',
		'color:#b91c1c',
		'font-size:13px',
		'text-align:center',
	].join(';');
	errorBox.textContent = `图表渲染失败：${message}`;
	element.appendChild(errorBox);
}

function disposeChartElement(element: HTMLElement) {
	const chart = echarts.getInstanceByDom(element);
	if (chart) chart.dispose();
}

export function useEchartsRenderer() {
	const controllers = new Map<HTMLElement, ContainerController>();

	function cleanupDetachedElements(controller: ContainerController) {
		for (const element of controller.observedElements) {
			if (controller.container.contains(element)) continue;
			controller.resizeObserver.unobserve(element);
			disposeChartElement(element);
			controller.observedElements.delete(element);
		}
	}

	function observeChartElement(
		controller: ContainerController,
		element: HTMLElement,
	) {
		if (controller.observedElements.has(element)) return;
		controller.observedElements.add(element);
		controller.resizeObserver.observe(element);
	}

	function renderChartElement(
		controller: ContainerController,
		element: HTMLElement,
	) {
		observeChartElement(controller, element);

		const rawConfig = element.getAttribute('data-echarts-config');
		if (!rawConfig) return;
		if (element.dataset.echartsRenderError) return;

		const { width, height } = element.getBoundingClientRect();
		if (width <= 0 || height <= 0) return;

		try {
			const code = decodeChartConfig(rawConfig);
			if (!code.trim()) return;

			const options = JSON.parse(code) as Record<string, unknown>;
			if (!options.color) {
				options.color = EXTENDED_COLORS;
			}

			const chart = echarts.getInstanceByDom(element) || echarts.init(element);
			chart.setOption(options, true);
			element.removeAttribute('data-echarts-config');
			element.dataset.echartsRendered = 'true';
		} catch (error) {
			console.error('ECharts rendering error:', error);
			showChartError(element, error);
		}
	}

	function scanContainer(controller: ContainerController) {
		controller.renderRafId = null;
		cleanupDetachedElements(controller);

		const elements =
			controller.container.querySelectorAll<HTMLElement>('.md-echarts');
		elements.forEach((element) => renderChartElement(controller, element));
	}

	function scheduleScan(controller: ContainerController) {
		if (controller.renderRafId !== null) return;
		controller.renderRafId = requestAnimationFrame(() =>
			scanContainer(controller),
		);
	}

	function createController(container: HTMLElement): ContainerController {
		const resizeObserver = new ResizeObserver((entries) => {
			for (const entry of entries) {
				const element = entry.target as HTMLElement;
				const chart = echarts.getInstanceByDom(element);
				if (chart) chart.resize();
				else if (element.hasAttribute('data-echarts-config'))
					scheduleScan(controller);
			}
		});
		const mutationObserver = new MutationObserver(() => scheduleScan(controller));

		const controller: ContainerController = {
			container,
			mutationObserver,
			resizeObserver,
			observedElements: new Set(),
			renderRafId: null,
		};

		mutationObserver.observe(container, {
			childList: true,
			subtree: true,
			attributes: true,
			attributeFilter: ['data-echarts-config'],
		});
		return controller;
	}

	function renderECharts(container: HTMLElement | null) {
		if (!container || typeof window === 'undefined') return;

		let controller = controllers.get(container);
		if (!controller) {
			controller = createController(container);
			controllers.set(container, controller);
		}

		nextTick(() => {
			if (container.isConnected) scheduleScan(controller);
		});
	}

	function disposeEChartsInContainer(container: HTMLElement | null) {
		if (!container) return;
		const controller = controllers.get(container);
		if (controller) {
			if (controller.renderRafId !== null)
				cancelAnimationFrame(controller.renderRafId);
			controller.mutationObserver.disconnect();
			controller.resizeObserver.disconnect();
			controller.observedElements.forEach(disposeChartElement);
			controller.observedElements.clear();
			controllers.delete(container);
			return;
		}

		container
			.querySelectorAll<HTMLElement>('.md-echarts')
			.forEach(disposeChartElement);
	}

	onBeforeUnmount(() => {
		for (const container of [...controllers.keys()]) {
			disposeEChartsInContainer(container);
		}
	});

	return { renderECharts, disposeEChartsInContainer };
}
