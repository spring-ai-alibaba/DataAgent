import 'highlight.js/styles/atom-one-light.css';
import hljs from 'highlight.js/lib/core';
import Sql from 'highlight.js/lib/languages/sql';
import Python from 'highlight.js/lib/languages/python';
import Json from 'highlight.js/lib/languages/json';

// 代码块复制函数
if (typeof window !== 'undefined' && !window.copyCodeBlock) {
  window.copyCodeBlock = btn => {
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

hljs.registerLanguage('sql', Sql);
hljs.registerLanguage('json', Json);
hljs.registerLanguage('python', Python);

const highlightPlugin = md => {
  md.renderer.rules.fence = (tokens, idx) => {
    const token = tokens[idx];
    const code = token.content;
    const lang = token.info;
    const langObj = hljs.getLanguage(lang);
    let cnt;
    if (langObj) {
      cnt = hljs.highlight(lang, code).value;
    } else {
      cnt = hljs.highlightAuto(code).value;
    }

    // HTML转义函数
    const escapeHtml = text => {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    };

    return `<div class="code-block-wrapper">
      <div class="code-block-header">
        <span class="code-language">${lang ? lang.toUpperCase() : 'TEXT'}</span>
        <button class="code-copy-button" onclick="copyCodeBlock(this)" data-code="${escapeHtml(code)}">
          复制
        </button>
      </div>
      <pre class="hljs"><code class="language-${lang}">${cnt}</code></pre>
    </div>`;
  };
};

export default highlightPlugin;
