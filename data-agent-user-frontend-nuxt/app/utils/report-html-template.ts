/*
 * Copyright 2026 the original author or authors.
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

function escapeHtmlAttribute(value: string): string {
	return value
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;');
}

export function buildReportHtml(
	renderedContent: string,
	echartsScriptUrl: string,
): string {
	const safeScriptUrl = escapeHtmlAttribute(echartsScriptUrl);

	return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>分析报告</title>
<script src="${safeScriptUrl}" onerror="window.__echartsLoadFailed = true"></script>
<style>
* { box-sizing: border-box; }
body { margin: 0; padding: 20px; background-color: #f3f4f6; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; color: #374151; line-height: 1.6; }
.container { max-width: 900px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); }
h1 { font-size: 2.25rem; font-weight: 800; color: #1e3a8a; margin-top: 0; margin-bottom: 1.5rem; border-bottom: 2px solid #e5e7eb; padding-bottom: 0.5rem; }
h2 { font-size: 1.5rem; font-weight: 700; color: #2563eb; margin-top: 2.5rem; margin-bottom: 1rem; border-left: 5px solid #2563eb; padding-left: 12px; }
h3 { font-size: 1.25rem; font-weight: 600; color: #1f2937; margin-top: 1.5rem; margin-bottom: 0.75rem; }
p { margin-bottom: 1rem; }
ul, ol { margin-bottom: 1rem; padding-left: 1.5rem; }
li { margin-bottom: 0.25rem; }
table { width: 100%; border-collapse: collapse; margin: 10px 0; display: block; overflow-x: auto; }
thead { display: table-header-group; }
tbody { display: table-row-group; }
tr { display: table-row; border-top: 1px solid #c6cbd1; }
th { display: table-cell; background: #f1f5f9; padding: 8px 12px; border: 1px solid #e2e8f0; font-weight: 600; font-size: 13px; text-align: left; }
td { display: table-cell; padding: 8px 12px; border: 1px solid #e8edf2; font-size: 13px; }
tr:nth-child(even) td { background: #f8fafc; }
code { background-color: #f1f5f9; padding: 0.2rem 0.4rem; border-radius: 0.25rem; font-size: 0.875em; color: #d946ef; font-family: monospace; }
pre { background: #1e293b; color: #f8fafc; padding: 1rem; border-radius: 0.5rem; overflow-x: auto; }
pre code { background: transparent; color: inherit; padding: 0; }
.md-echarts { width: 100%; min-height: 400px; margin: 30px 0; border: 1px solid #e2e8f0; border-radius: 8px; background-color: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.chart-error { display: flex; align-items: center; justify-content: center; height: 100%; min-height: 160px; padding: 16px; color: #b91c1c; background-color: #fef2f2; border: 1px dashed #ef4444; border-radius: 8px; text-align: center; }
</style>
</head>
<body>
<div class="container">${renderedContent}</div>
<script>
(function renderCharts() {
  var boxes = document.querySelectorAll('.md-echarts');
  function showError(box, message) {
    box.innerHTML = '';
    var error = document.createElement('div');
    error.className = 'chart-error';
    error.textContent = message;
    box.appendChild(error);
  }

  if (window.__echartsLoadFailed || typeof echarts === 'undefined') {
    boxes.forEach(function(box) {
      showError(box, '图表组件加载失败，请重新打开报告');
    });
    return;
  }

  boxes.forEach(function(box) {
    try {
      var code = box.getAttribute('data-echarts-config');
      if (!code) throw new Error('缺少图表配置');
      var option = new Function('return (' + code + ')')();
      var chart = echarts.init(box);
      chart.setOption(option);
      box.removeAttribute('data-echarts-config');
      window.addEventListener('resize', function() { chart.resize(); });
    } catch (error) {
      showError(box, '图表渲染失败：' + (error && error.message ? error.message : String(error)));
    }
  });
})();
</script>
</body>
</html>`;
}
