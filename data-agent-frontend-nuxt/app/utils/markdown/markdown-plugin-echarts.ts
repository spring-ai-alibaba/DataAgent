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

import type MarkdownIt from 'markdown-it';

const echartsPlugin = (md: MarkdownIt) => {
	const originalFence = md.renderer.rules.fence!.bind(md.renderer.rules);
	md.renderer.rules.fence = (tokens, idx, options, env, slf) => {
		const token = tokens[idx]!;
		if (token.info.trim() === 'echarts') {
			const code = token.content.trim();
			const braceOpen = code.match(/\{/g)?.length ?? 0;
			const braceClose = code.match(/\}/g)?.length ?? 0;
			const hasValidContent =
				/\{[\s\S]*\}/.test(code) && braceOpen === braceClose;

			if (hasValidContent) {
				// Store raw code in data attribute so the renderer can eval it (supports functions)
				const escaped = code
					.replace(/&/g, '&amp;')
					.replace(/</g, '&lt;')
					.replace(/>/g, '&gt;')
					.replace(/"/g, '&quot;');
				const width = '100%';
				const height = 400;
				return `<div style="width:${width};height:${height}px" class="md-echarts" data-echarts-config="${escaped}"></div>`;
			} else if (code.length > 0) {
				// Chart code is still being streamed — show a skeleton placeholder
				return `<div class="md-echarts-skeleton">
					<span class="md-echarts-skeleton-icon">⏳</span>
					<span class="md-echarts-skeleton-text">图表生成中...</span>
				</div>`;
			} else {
				return `<pre><code class="language-echarts">${code}</code></pre>`;
			}
		}
		return originalFence(tokens, idx, options, env, slf);
	};
};

export default echartsPlugin;
