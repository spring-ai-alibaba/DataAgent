import 'highlight.js/styles/atom-one-light.css';
import hljs from 'highlight.js/lib/core';
import Sql from 'highlight.js/lib/languages/sql';
import Python from 'highlight.js/lib/languages/python';
import Json from 'highlight.js/lib/languages/json';
import JavaScript from 'highlight.js/lib/languages/javascript';
import type MarkdownIt from 'markdown-it';

hljs.registerLanguage('sql', Sql);
hljs.registerLanguage('json', Json);
hljs.registerLanguage('python', Python);
hljs.registerLanguage('javascript', JavaScript);

declare global {
	interface Window {
		copyCodeBlock?: (btn: HTMLElement) => void;
	}
}

const highlightPlugin = (md: MarkdownIt) => {
	const originalFence = md.renderer.rules.fence!.bind(md.renderer.rules);
	md.renderer.rules.fence = (tokens, idx, options, env, slf) => {
		const token = tokens[idx];
		const code = token.content;
		const lang = token.info.trim();

		if (lang === 'echarts') {
			return originalFence(tokens, idx, options, env, slf);
		}

		const langObj = hljs.getLanguage(lang);
		let highlighted: string;
		if (langObj) {
			highlighted = hljs.highlight(code, { language: lang }).value;
		} else {
			highlighted = hljs.highlightAuto(code).value;
		}

		const escapeAttr = (text: string): string => {
			return text
				.replace(/&/g, '&amp;')
				.replace(/</g, '&lt;')
				.replace(/>/g, '&gt;')
				.replace(/"/g, '&quot;')
				.replace(/'/g, '&#039;');
		};

		const langLabel = lang ? lang.toUpperCase() : 'TEXT';
		const langClass = lang ? lang.toLowerCase().replace(/[^a-z0-9_-]+/g, '') : 'plaintext';

		return `<div class="code-block-wrapper">
			<div class="code-block-header">
				<span class="code-language">${escapeAttr(langLabel)}</span>
				<button class="code-copy-button" onclick="copyCodeBlock(this)" data-code="${escapeAttr(code)}">
					复制
				</button>
			</div>
			<pre class="hljs"><code class="language-${langClass}">${highlighted}</code></pre>
		</div>`;
	};
};

if (typeof window !== 'undefined' && !window.copyCodeBlock) {
	window.copyCodeBlock = (btn: HTMLElement) => {
		const code = btn.getAttribute('data-code');
		if (!code) return;

		const originalText = btn.textContent;
		const parser = new DOMParser();
		const decodedCode = parser
			.parseFromString(`<div>${code}</div>`, 'text/html')
			.querySelector('div')?.textContent;

		if (!decodedCode) return;

		navigator.clipboard
			.writeText(decodedCode)
			.then(() => {
				btn.textContent = '已复制!';
				btn.classList.add('copied');
				setTimeout(() => {
					btn.textContent = originalText;
					btn.classList.remove('copied');
				}, 2000);
			})
			.catch(() => {
				btn.textContent = '复制失败';
				setTimeout(() => {
					btn.textContent = originalText;
				}, 2000);
			});
	};
}

export default highlightPlugin;
