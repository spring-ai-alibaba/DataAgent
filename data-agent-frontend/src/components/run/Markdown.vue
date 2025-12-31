<!--
 * Copyright 2025 the original author or authors.
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
-->

<template>
  <div class="markdown-container">
    <div class="markdown-content" v-html="renderedHtml" :key="contentKey"></div>
  </div>
</template>

<script lang="ts">
  import { defineComponent, computed, watch } from 'vue';
  import { marked } from 'marked';
  import DOMPurify from 'dompurify';
  import hljs from 'highlight.js';
  import 'highlight.js/styles/github.css';

  export default defineComponent({
    name: 'Markdown',
    props: {
      content: {
        type: String,
        default: '',
      },
      generating: {
        type: Boolean,
        default: false,
      },
    },
    setup(props, { slots }) {
      // HTML 转义函数
      const escapeHtml = (text: string): string => {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
      };

      // 创建自定义渲染器
      const createRenderer = () => {
        const renderer = new marked.Renderer();

        // 重写代码块渲染
        renderer.code = (code: string, language: string | undefined) => {
          const lang = language || 'text';

          // 尝试高亮代码
          let highlightedCode = code;
          if (lang && lang !== 'text') {
            try {
              if (hljs.getLanguage(lang)) {
                highlightedCode = hljs.highlight(code, { language: lang }).value;
              } else {
                // 如果语言不支持，转义 HTML
                highlightedCode = escapeHtml(code);
              }
            } catch (err) {
              // 如果高亮失败，转义 HTML
              highlightedCode = escapeHtml(code);
            }
          } else {
            highlightedCode = escapeHtml(code);
          }

          return `
            <div class="code-block-wrapper">
              <div class="code-block-header">
                <span class="code-language">${lang.toUpperCase()}</span>
                <button class="code-copy-button" onclick="copyCodeBlock(this)" data-code="${escapeHtml(code)}">
                  复制
                </button>
              </div>
              <pre class="hljs"><code class="language-${lang}">${highlightedCode}</code></pre>
            </div>
          `;
        };

        // 重写行内代码渲染
        renderer.codespan = (code: string) => {
          return `<code class="inline-code">${escapeHtml(code)}</code>`;
        };

        // 重写链接渲染，添加 target="_blank"
        renderer.link = (href: string, title: string | null, text: string) => {
          const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
          return `<a href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer"${titleAttr}>${text}</a>`;
        };

        return renderer;
      };

      // 配置 marked（只配置一次）
      marked.setOptions({
        gfm: true, // GitHub Flavored Markdown
        breaks: true, // 支持换行
      });

      // 从插槽中提取文本内容
      const getSlotText = (): string => {
        if (!slots.default) {
          return '';
        }
        try {
          const slotNodes = slots.default();
          if (!slotNodes || slotNodes.length === 0) {
            return '';
          }

          // 提取所有文本节点
          const extractText = (node: any): string => {
            if (node === null || node === undefined) {
              return '';
            }

            // 如果是字符串，直接返回
            if (typeof node === 'string') {
              return node;
            }

            // 如果是数字，转换为字符串
            if (typeof node === 'number') {
              return String(node);
            }

            // 如果是对象
            if (node && typeof node === 'object') {
              // Vue 3 的文本节点
              if (node.type === 3 || node.type === 'Text') {
                return node.children || node.text || '';
              }

              // 如果有 children 属性
              if (node.children) {
                if (typeof node.children === 'string') {
                  return node.children;
                }
                if (Array.isArray(node.children)) {
                  return node.children.map(extractText).join('');
                }
              }

              // 如果有 text 属性
              if (node.text) {
                return node.text;
              }

              // 如果是数组（某些情况下）
              if (Array.isArray(node)) {
                return node.map(extractText).join('');
              }
            }

            return '';
          };

          return slotNodes.map(extractText).join('');
        } catch (error) {
          console.error('Error extracting slot text:', error);
          return '';
        }
      };

      // 计算渲染后的 HTML
      const renderedHtml = computed(() => {
        // 优先使用插槽内容，如果没有则使用 prop
        let content = '';
        const slotText = getSlotText();
        if (slotText) {
          content = slotText;
        } else {
          content = props.content || '';
        }

        if (!content.trim()) {
          return '';
        }

        try {
          // 每次计算时创建新的 renderer，确保响应式
          const renderer = createRenderer();
          // 使用 marked.parse 或 marked() 都可以，但需要传入 renderer
          const rawHtml = marked.parse(content, { renderer }) as string;
          if (!rawHtml) {
            console.warn('Marked returned empty string');
            return '';
          }
          const sanitized = DOMPurify.sanitize(rawHtml);
          return sanitized;
        } catch (error) {
          console.error('Markdown rendering error:', error);
          // 如果渲染失败，返回转义后的纯文本
          return DOMPurify.sanitize(content.replace(/\n/g, '<br>'));
        }
      });

      // 在组件挂载时添加全局复制函数
      const setupCopyFunction = () => {
        if (typeof window !== 'undefined' && !(window as any).copyCodeBlock) {
          (window as any).copyCodeBlock = (btn: HTMLElement) => {
            const code = btn.getAttribute('data-code');
            if (!code) return;

            const originalText = btn.textContent;

            navigator.clipboard
              .writeText(code)
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
      };

      // 用于强制更新的 key
      const contentKey = computed(() => {
        // 使用内容长度和哈希值作为 key，确保内容变化时组件会更新
        const slotText = getSlotText();
        const content = slotText || props.content || '';
        return content ? `${content.length}-${content.slice(0, 50)}` : 'empty';
      });

      // 监听内容变化，触发重新渲染
      // 注意：computed 属性会自动响应 props.content 和插槽内容的变化
      watch(
        () => {
          // 同时监听 prop 和插槽内容
          return getSlotText() || props.content;
        },
        newContent => {
          // 内容变化时，computed 属性会自动重新计算
          // 这里可以添加调试日志
          if (process.env.NODE_ENV === 'development') {
            console.log('Markdown content updated, length:', newContent?.length || 0);
          }
        },
      );

      // 组件挂载时设置复制函数
      setupCopyFunction();

      return {
        renderedHtml,
        contentKey,
      };
    },
  });
</script>

<style scoped>
  .markdown-container {
    width: 100%;
    /* 确保样式优先级，防止被父容器样式覆盖 */
    line-height: 1.4 !important;
  }

  .markdown-content {
    font-size: 0.75em; /* 缩小2个级别：从默认16px缩小到12px */
    line-height: 1.4 !important; /* 缩小行间距，使用 !important 确保不被覆盖 */
    color: #1f2933;
    word-wrap: break-word;
    /* 重置可能被父容器影响的样式 */
    white-space: normal;
    font-family: inherit;
  }

  /* 标题样式 */
  .markdown-content :deep(h1),
  .markdown-content :deep(h2),
  .markdown-content :deep(h3),
  .markdown-content :deep(h4),
  .markdown-content :deep(h5),
  .markdown-content :deep(h6) {
    margin-top: 16px; /* 缩小间距 */
    margin-bottom: 10px; /* 缩小间距 */
    font-weight: 600;
    line-height: 1.2; /* 缩小行高 */
  }

  .markdown-content :deep(h1) {
    font-size: 2em;
    border-bottom: 1px solid #eaecef;
    padding-bottom: 0.3em;
  }

  .markdown-content :deep(h2) {
    font-size: 1.5em;
    border-bottom: 1px solid #eaecef;
    padding-bottom: 0.3em;
  }

  .markdown-content :deep(h3) {
    font-size: 1.25em;
  }

  .markdown-content :deep(h4) {
    font-size: 1em;
  }

  .markdown-content :deep(h5) {
    font-size: 0.875em;
  }

  .markdown-content :deep(h6) {
    font-size: 0.85em;
    color: #6a737d;
  }

  /* 段落样式 */
  .markdown-content :deep(p) {
    margin-top: 0 !important;
    margin-bottom: 8px !important; /* 进一步缩小间距 */
    line-height: 1.4 !important; /* 确保段落行高一致 */
  }

  /* 列表样式 */
  .markdown-content :deep(ul),
  .markdown-content :deep(ol) {
    margin-top: 0 !important;
    margin-bottom: 8px !important; /* 进一步缩小间距 */
    padding-left: 1.5em; /* 缩小缩进 */
    line-height: 1.4 !important;
  }

  .markdown-content :deep(li) {
    margin-bottom: 0.15em !important; /* 进一步缩小间距 */
    line-height: 1.4 !important;
  }

  .markdown-content :deep(li > p) {
    margin-top: 6px !important; /* 进一步缩小间距 */
    margin-bottom: 6px !important;
    line-height: 1.4 !important;
  }

  /* 代码块样式 */
  .markdown-content :deep(.code-block-wrapper) {
    margin: 10px 0; /* 缩小间距 */
    border: 1px solid #e1e4e8;
    border-radius: 6px;
    overflow: hidden;
    background: #f6f8fa;
  }

  .markdown-content :deep(.code-block-header) {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #f6f8fa;
    padding: 6px 10px; /* 缩小内边距 */
    border-bottom: 1px solid #e1e4e8;
    font-size: 11px; /* 缩小字体 */
  }

  .markdown-content :deep(.code-language) {
    color: #6a737d;
    font-weight: 600;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 10px; /* 缩小字体 */
    text-transform: uppercase;
  }

  .markdown-content :deep(.code-copy-button) {
    background: transparent;
    border: 1px solid #d1d5da;
    padding: 3px 10px; /* 缩小内边距 */
    border-radius: 4px;
    font-size: 10px; /* 缩小字体 */
    cursor: pointer;
    transition: all 0.2s;
    color: #24292e;
  }

  .markdown-content :deep(.code-copy-button:hover) {
    background: #f3f4f6;
    border-color: #c6cbd1;
  }

  .markdown-content :deep(.code-copy-button.copied) {
    background: #28a745;
    border-color: #28a745;
    color: white;
  }

  .markdown-content :deep(pre) {
    margin: 0;
    padding: 10px; /* 缩小内边距 */
    overflow: auto;
    background: #f6f8fa;
    font-size: 11px; /* 缩小字体 */
    line-height: 1.35; /* 缩小行高 */
  }

  .markdown-content :deep(pre code) {
    display: block;
    padding: 0;
    margin: 0;
    background: transparent;
    border: none;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  }

  /* 行内代码样式 */
  .markdown-content :deep(.inline-code),
  .markdown-content :deep(code:not(pre code)) {
    background: #f6f8fa;
    border: 1px solid #e1e4e8;
    border-radius: 3px;
    padding: 2px 6px;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 85%;
    color: #e83e8c;
  }

  /* 引用样式 */
  .markdown-content :deep(blockquote) {
    padding: 0 0.75em; /* 缩小内边距 */
    color: #6a737d;
    border-left: 0.25em solid #dfe2e5;
    margin: 0 0 10px 0; /* 缩小间距 */
  }

  .markdown-content :deep(blockquote > :first-child) {
    margin-top: 0;
  }

  .markdown-content :deep(blockquote > :last-child) {
    margin-bottom: 0;
  }

  /* 表格样式 */
  .markdown-content :deep(table) {
    border-collapse: collapse;
    border-spacing: 0;
    width: 100%;
    margin: 10px 0; /* 缩小间距 */
    display: block;
    overflow-x: auto;
  }

  .markdown-content :deep(thead) {
    display: table-header-group;
  }

  .markdown-content :deep(tbody) {
    display: table-row-group;
  }

  .markdown-content :deep(tr) {
    display: table-row;
    border-top: 1px solid #c6cbd1;
  }

  .markdown-content :deep(tr:nth-child(2n)) {
    background-color: #f6f8fa;
  }

  .markdown-content :deep(th),
  .markdown-content :deep(td) {
    display: table-cell;
    padding: 4px 10px; /* 缩小内边距 */
    border: 1px solid #dfe2e5;
  }

  .markdown-content :deep(th) {
    font-weight: 600;
    background-color: #f6f8fa;
  }

  /* 链接样式 */
  .markdown-content :deep(a) {
    color: #0366d6;
    text-decoration: none;
  }

  .markdown-content :deep(a:hover) {
    text-decoration: underline;
  }

  /* 分隔线样式 */
  .markdown-content :deep(hr) {
    height: 1px;
    padding: 0;
    margin: 16px 0; /* 缩小间距 */
    background-color: transparent;
    border: 0;
    border-top: 1px dashed #e1e4e8;
  }

  /* 图片样式 */
  .markdown-content :deep(img) {
    max-width: 100%;
    height: auto;
    border-style: none;
    margin: 10px 0; /* 缩小间距 */
  }

  /* 强调样式 */
  .markdown-content :deep(strong) {
    font-weight: 600;
  }

  .markdown-content :deep(em) {
    font-style: italic;
  }

  /* 删除线样式 */
  .markdown-content :deep(del) {
    text-decoration: line-through;
  }
</style>
