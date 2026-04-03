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
