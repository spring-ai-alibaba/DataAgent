/*
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
 */

// 与后端 ReportTemplateUtil 一致的 CDN 地址
const MARKED_URL = 'https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js';
const ECHARTS_URL = 'https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js';

// raw-markdown 中的内容需转义，以便 innerText 能正确读取原始 Markdown
function escapeForHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function buildReportHtml(markdownContent: string): string {
  const escapedContent = escapeForHtml(markdownContent);

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>分析报告</title>
<script src="${MARKED_URL}"></script>
<script src="${ECHARTS_URL}"></script>
<style>
* { box-sizing: border-box; }
body { margin: 0; padding: 20px; background-color: #f3f4f6; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; color: #374151; line-height: 1.6; }
.container { max-width: 900px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); }
h1 { font-size: 2.25rem; font-weight: 800; color: #1e3a8a; margin-top: 0; margin-bottom: 1.5rem; border-bottom: 2px solid #e5e7eb; padding-bottom: 0.5rem; }
h2 { font-size: 1.5rem; font-weight: 700; color: #2563eb; margin-top: 2.5rem; margin-bottom: 1rem; border-left: 5px solid #2563eb; padding-left: 12px; }
h3 { font-size: 1.25rem; font-weight: 600; color: #1f2937; margin-top: 1.5rem; margin-bottom: 0.75rem; }
p { margin-bottom: 1rem; }
ul, ol { margin-bottom: 1rem; padding-left: 1.5rem; }
li { margin-bottom: 0.25rem; }
code { background-color: #f1f5f9; padding: 0.2rem 0.4rem; border-radius: 0.25rem; font-size: 0.875em; color: #d946ef; font-family: monospace; }
pre { background: #1e293b; color: #f8fafc; padding: 1rem; border-radius: 0.5rem; overflow-x: auto; }
pre code { background: transparent; color: inherit; padding: 0; }
.chart-box { width: 100%; height: 450px; margin: 30px 0; border: 1px solid #e2e8f0; border-radius: 8px; background-color: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.chart-error { display: flex; align-items: center; justify-content: center; height: 100%; color: #ef4444; background-color: #fef2f2; border: 1px dashed #ef4444; border-radius: 8px; }
</style>
</head>
<body>
<div class="container">
<div id="raw-markdown" style="display:none;">${escapedContent}</div>
<div id="render-target" class="markdown-body"></div>
</div>
<script>
window.onload = function() {
  if (typeof marked === 'undefined') {
    document.getElementById('render-target').innerHTML = '<p style="color:red;">Marked库加载失败，请检查网络</p>';
    return;
  }
  var rawDiv = document.getElementById('raw-markdown');
  if (!rawDiv) return;
  var rawText = rawDiv.innerText;
  var renderer = new marked.Renderer();
  renderer.code = function(code, language) {
    if (language === 'echarts' || language === 'json') {
      var id = 'chart_' + Math.random().toString(36).substr(2, 9);
      return '<div id="' + id + '" class="chart-box" data-option="' + encodeURIComponent(code) + '"></div>';
    }
    return '<pre><code class="language-' + language + '">' + code.replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</code></pre>';
  };
  document.getElementById('render-target').innerHTML = marked.parse(rawText, { renderer: renderer });
  if (typeof echarts !== 'undefined') {
    document.querySelectorAll('.chart-box').forEach(function(box) {
      try {
        var code = decodeURIComponent(box.getAttribute('data-option'));
        var option = new Function('return ' + code)();
        var myChart = echarts.init(box);
        myChart.setOption(option);
        window.addEventListener('resize', function() { myChart.resize(); });
      } catch(e) {
        box.innerHTML = '<div class="chart-error"><b>图表渲染错误</b><br/>' + e.message + '</div>';
      }
    });
  }
};
</script>
</body>
</html>`;
}
