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
    <div class="markdown-content" ref="markdown-agent-container" @click="hdlClick" />
  </div>
</template>

<script lang="ts">
  import MarkdownIt from 'markdown-it';
  import MarkdownItContainer from 'markdown-it-container';
  import MarkdownPluginEcharts from './markdown-plugin-echarts.ts';
  import MarkdownPluginHighlight from './markdown-plugin-highlight.ts';

  import * as echarts from 'echarts';
  import { EXTENDED_COLORS } from '../charts/BaseChart';
  import { defineComponent } from 'vue';

  // 防抖函数
  function debounce<T extends (...args: any[]) => any>(
    func: T,
    wait: number,
  ): (...args: Parameters<T>) => void {
    let timeout: NodeJS.Timeout | null = null;
    return function executedFunction(...args: Parameters<T>) {
      const later = () => {
        if (timeout) {
          clearTimeout(timeout);
        }
        func(...args);
      };
      if (timeout) {
        clearTimeout(timeout);
      }
      timeout = setTimeout(later, wait);
    };
  }

  const DEFAULT_OPTIONS_LINK_ATTRIBUTES = {
    attrs: {
      target: '_blank',
      rel: 'noopener',
    },
  };

  // 定义组件选项类型
  interface Options {
    markdownIt?: {
      linkify?: boolean;
      [key: string]: any;
    };
    linkAttributes?: {
      attrs: {
        target: string;
        rel: string;
      };
    };
  }

  export default defineComponent({
    name: 'MarkdownAgentContainer',
    props: {
      content: {
        type: String,
        default: '',
      },
      options: {
        type: Object as () => Options,
        default() {
          return {
            markdownIt: {
              linkify: true,
            },
            linkAttributes: DEFAULT_OPTIONS_LINK_ATTRIBUTES,
          };
        },
      },
    },
    emits: ['render-complete'],
    data() {
      const optMarkdownIt = this.options.markdownIt;

      const md = new MarkdownIt(optMarkdownIt)
        .use(MarkdownPluginHighlight)
        .use(MarkdownPluginEcharts)
        .use(MarkdownItContainer);
      return {
        md,
        showViewer: false,
        index: 0,
        urlList: [] as string[],
        renderECharts: () => {}, // 初始化 renderECharts 方法
      };
    },
    created() {
      // 创建防抖版本的ECharts渲染函数
      this.renderECharts = debounce(() => {
        // render echarts - 只有当内容完整时才渲染
        const echartsElements = document.querySelectorAll('.md-echarts');
        echartsElements.forEach(element => {
          try {
            const content = element.textContent;
            if (!content || content.trim() === '') {
              return;
            }
            // 再次验证JSON结构是否完整
            const hasValidJson =
              /\{[\s\S]*\}/.test(content) &&
              content.match(/\{/g)?.length === content.match(/\}/g)?.length;
            if (hasValidJson) {
              const options = JSON.parse(content);
              if (!options.color) {
                options.color = EXTENDED_COLORS;
              }
              const existingChart = echarts.getInstanceByDom(element);
              if (existingChart) {
                // 复用已存在的图表实例，避免重复初始化导致的内存泄漏
                existingChart.setOption(options, true);
              } else {
                const chart = echarts.init(element);
                chart.setOption(options);
              }
            } else {
              // 如果JSON不完整，不做任何处理，保持原始状态
              console.log(
                'ECharts configuration is incomplete, skipping rendering',
                element.textContent,
              );
            }
          } catch (e) {
            // 只在控制台记录错误，不影响用户界面
            console.error('ECharts rendering error:', e);
            // 不替换元素，保持原始内容，等待完整数据
          }
        });
      }, 500); // 500毫秒内不重复检查
    },

    watch: {
      content: {
        immediate: true,
        handler(val: string) {
          this.$nextTick(() => {
            const container = this.$refs['markdown-agent-container'] as HTMLElement;
            if (container) {
              container.innerHTML = this.md.render(val);
              // 调用防抖后的ECharts渲染函数
              this.renderECharts();
              const list: string[] = [];
              this.urlList = list;
              // emit event
              this.$emit('render-complete');
            }
          });
        },
      },
    },
    methods: {
      use(plugin: any, options?: any) {
        this.md.use(plugin, options);
      },
      get() {
        return this.md;
      },
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
    font-size: 0.85em; /* 缩小2个级别：从默认16px缩小到12px */
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
