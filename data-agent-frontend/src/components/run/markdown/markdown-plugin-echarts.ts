/*
 * Copyright 2024-2025 the original author or authors.
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

import type { MarkdownIt } from 'markdown-it';

export default (md: MarkdownIt) => {
  const temp = md.renderer.rules.fence.bind(md.renderer.rules);
  md.renderer.rules.fence = (tokens, idx, options, env, slf) => {
    const token = tokens[idx];
    if (token.info === 'echarts') {
      const code = token.content.trim();
      // 检查是否有完整的JSON结构，这里简单检查是否包含至少一个完整的键值对和正确的闭合括号
      const hasValidJson =
        /\{[\s\S]*\}/.test(code) && code.match(/\{/g)?.length === code.match(/\}/g)?.length;
      if (hasValidJson) {
        try {
          // 使用 new Function 验证配置（支持包含函数表达式的 ECharts 配置）
          // 与 report-html-template.ts 保持一致的解析方式
          new Function('return (' + code + ')')();
          const width = '100%';
          const height = 400;
          // 使用 data-option + encodeURIComponent 存储原始配置，避免 HTML 转义问题
          return `<div style="width:${width};height:${height}px" class="md-echarts" data-option="${encodeURIComponent(code)}"></div>`;
        } catch (e) {
          // JS 解析失败，提示配置格式错误
          return `<pre>echarts配置格式错误: ${(e as Error).message}</pre>`;
        }
      } else {
        // 如果JSON结构不完整，返回原始代码块，不进行渲染
        return `<pre><code class="language-echarts md-echarts">${code}</code></pre>`;
      }
    }
    return temp(tokens, idx, options, env, slf);
  };
};
