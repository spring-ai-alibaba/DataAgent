import MarkdownIt from 'markdown-it';
import MarkdownItContainer from 'markdown-it-container';
import highlightPlugin from './markdown-plugin-highlight';
import echartsPlugin from './markdown-plugin-echarts';

const md = new MarkdownIt({
	linkify: true,
	breaks: true,
	html: false,
})
	.use(highlightPlugin)
	.use(echartsPlugin)
	.use(MarkdownItContainer);

export function renderMarkdownContent(content: string): string {
	if (!content) return '';
	return md.render(content);
}

export { md };
