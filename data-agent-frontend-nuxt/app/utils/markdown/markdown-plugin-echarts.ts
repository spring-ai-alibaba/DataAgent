import type MarkdownIt from 'markdown-it';

const echartsPlugin = (md: MarkdownIt) => {
	const originalFence = md.renderer.rules.fence!.bind(md.renderer.rules);
	md.renderer.rules.fence = (tokens, idx, options, env, slf) => {
		const token = tokens[idx]!;
		if (token.info.trim() === 'echarts') {
			const code = token.content.trim();
			const hasValidJson =
				/\{[\s\S]*\}/.test(code) && code.match(/\{/g)?.length === code.match(/\}/g)?.length;
			if (hasValidJson) {
				try {
					const json = JSON.parse(code);
					const width = '100%';
					const height = 400;
					return `<div style="width:${width};height:${height}px" class="md-echarts">${JSON.stringify(json)}</div>`;
				} catch (e) {
					return `<pre>echarts配置格式错误: ${(e as Error).message}</pre>`;
				}
			} else {
				return `<pre><code class="language-echarts">${code}</code></pre>`;
			}
		}
		return originalFence(tokens, idx, options, env, slf);
	};
};

export default echartsPlugin;
