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

import 'highlight.js/styles/atom-one-light.css';
import hljs from 'highlight.js/lib/core';
import Sql from 'highlight.js/lib/languages/sql';
import Python from 'highlight.js/lib/languages/python';
import Json from 'highlight.js/lib/languages/json';
import type { MarkdownIt } from 'markdown-it';

hljs.registerLanguage('sql', Sql);
hljs.registerLanguage('json', Json);
hljs.registerLanguage('python', Python);

// 扩展 Window 接口以包含 copyCodeBlock 方法
declare global {
  interface Window {
    copyCodeBlock?: (btn: HTMLElement) => void;
  }
}

const highlightPlugin = (md: MarkdownIt) => {
  md.renderer.rules.fence = (tokens, idx) => {
    const token = tokens[idx];
    const code = token.content;
    const lang = token.info;
    const langObj = hljs.getLanguage(lang);
    let cnt: string;
    if (langObj) {
      cnt = hljs.highlight(lang, code).value;
    } else {
      cnt = hljs.highlightAuto(code).value;
    }

    // 使用HTML实体编码确保特殊字符被正确处理
    const escapeHtml = (text: string): string => {
      return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    };
    const rawLang = typeof lang === 'string' ? lang : '';
    const safeLangForAttr = rawLang.toLowerCase().replace(/[^a-z0-9_-]+/g, '');
    const langClass = safeLangForAttr || 'plaintext';
    const langLabel = lang ? lang.toUpperCase() : 'TEXT';

    return `<div class="code-block-wrapper">
      <div class="code-block-header">
        <span class="code-language">${escapeHtml(langLabel)}</span>
        <button class="code-copy-button" onclick="copyCodeBlock(this)" data-code="${escapeHtml(code)}">
          复制
        </button>
      </div>
      <pre class="hljs"><code class="language-${langClass}">${cnt}</code></pre>
    </div>`;
  };
};

// 代码块复制函数
if (typeof window !== 'undefined' && !window.copyCodeBlock) {
  window.copyCodeBlock = (btn: HTMLElement) => {
    const code = btn.getAttribute('data-code');
    if (!code) return;

    const originalText = btn.textContent;

    // 使用DOMParser解码HTML实体
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
