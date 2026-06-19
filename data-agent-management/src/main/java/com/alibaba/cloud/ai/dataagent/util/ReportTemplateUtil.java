/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.springframework.stereotype.Component;

@Component
public class ReportTemplateUtil {

	private final DataAgentProperties dataAgentProperties;

	public ReportTemplateUtil(DataAgentProperties dataAgentProperties) {
		this.dataAgentProperties = dataAgentProperties;
	}

	private static final String REPORT_TEMPLATE_HEADER = """
			<!DOCTYPE html>
			<html lang="zh-CN">
			<head>
			<meta charset="UTF-8">
			<meta name="viewport" content="width=device-width, initial-scale=1.0">
			<title>分析报告</title>

			<!-- ⚠️ 使用国内 Staticfile CDN 源，速度快且稳定 -->
			<!-- 1. Marked.js (Markdown 解析器) -->
			<script src="{{MARKED_URL}}"></script>

			<!-- 2. ECharts (图表库) -->
			<script src="{{ECHARTS_URL}}"></script>

			<style>
			 /* --- 替代 Tailwind 的手写样式开始 --- */

			               /* 1. 全局重置 */
			               * {
			                   box-sizing: border-box;
			               }
			               body {
			                   margin: 0;
			                   padding: 20px;
			                   background-color: #f3f4f6;
			                   font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
			                   color: #374151;
			                   line-height: 1.6;
			               }

			               /* 2. 报告容器 (白纸效果) */
			               .container {
			                   max-width: 900px;
			                   margin: 0 auto;
			                   background-color: #ffffff;
			                   padding: 40px;
			                   border-radius: 12px;
			                   box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
			               }

			               /* 3. 标题样式 */
			               h1 {
			                   font-size: 2.25rem;
			                   font-weight: 800;
			                   color: #1e3a8a;
			                   margin-top: 0;
			                   margin-bottom: 1.5rem;
			                   border-bottom: 2px solid #e5e7eb;
			                   padding-bottom: 0.5rem;
			               }
			               h2 {
			                   font-size: 1.5rem;
			                   font-weight: 700;
			                   color: #2563eb;
			                   margin-top: 2.5rem;
			                   margin-bottom: 1rem;
			                   border-left: 5px solid #2563eb;
			                   padding-left: 12px;
			               }
			               h3 {
			                   font-size: 1.25rem;
			                   font-weight: 600;
			                   color: #1f2937;
			                   margin-top: 1.5rem;
			                   margin-bottom: 0.75rem;
			               }

			               /* 4. 正文与列表 */
			               p { margin-bottom: 1rem; }
			               ul, ol { margin-bottom: 1rem; padding-left: 1.5rem; }
			               li { margin-bottom: 0.25rem; }

			               /* 5. 代码块样式 */
			               code {
			                   background-color: #f1f5f9;
			                   padding: 0.2rem 0.4rem;
			                   border-radius: 0.25rem;
			                   font-size: 0.875em;
			                   color: #d946ef;
			                   font-family: monospace;
			               }
			               pre {
			                   background: #1e293b;
			                   color: #f8fafc;
			                   padding: 1rem;
			                   border-radius: 0.5rem;
			                   overflow-x: auto;
			               }
			               pre code {
			                   background: transparent;
			                   color: inherit;
			                   padding: 0;
			               }

			               /* 6. 图表容器样式 */
			               .chart-box {
			                   width: 100%;
			                   height: 450px;
			                   margin: 30px 0;
			                   border: 1px solid #e2e8f0;
			                   border-radius: 8px;
			                   background-color: #fff;
			                   box-shadow: 0 1px 3px rgba(0,0,0,0.1);
			               }

			               /* 7. 错误提示样式 */
			               .chart-error {
			                   display: flex;
			                   align-items: center;
			                   justify-content: center;
			                   height: 100%;
			                   color: #ef4444;
			                   background-color: #fef2f2;
			                   border: 1px dashed #ef4444;
			                   border-radius: 8px;
			               }

			               /* 8. 表格样式 */
							table {
								width: 100%;
								border-collapse: collapse;
								margin: 1.5rem 0;
								background-color: #ffffff;
								border-radius: 0.5rem;
								overflow: hidden;
								box-shadow: 0 1px 3px rgba(0,0,0,0.1);
							}

							th, td {
								padding: 0.75rem 1rem;
								text-align: left;
								border: 1px solid #e5e7eb;
							}

							th {
								background-color: #f9fafb;
								font-weight: 600;
								color: #374151;
							}

							td {
								border-top: 1px solid #e5e7eb;
							}

							tr:nth-child(even) {
								background-color: #f9fafb;
							}

							tr:hover {
								background-color: #f3f4f6;
							}

			               /* --- 样式结束 --- */
			</style>
			</head>
			<body>
			<div class="container">
			<!-- 原始内容容器（隐藏），用于接收 LLM 的内容 -->
			<!-- 这里的 display:none 至关重要，防止显示原始 Markdown -->
			<div id="raw-markdown" style="display:none;">
			""";

	// FOOTER 部分
	// window.onload 会对llm生成的内容渲染成 HTML
	// 并且在渲染过程中，会检查是否是echarts数据，如果是echarts数据，则进行图表渲染
	// 文本保持原样。如果图片渲染失败降级显示原始内容
	private static final String REPORT_TEMPLATE_FOOTER = """
			</div> <!-- raw-markdown 结束 -->

			<!-- 渲染目标容器 -->
			<div id="render-target" class="markdown-body"></div>

			</div> <!-- container 结束 -->

			<script>
			  window.onload = function() {
			      // 0. 安全检查
			      if (typeof marked === 'undefined') {
			          alert('错误：Marked库加载失败，请检查网络或更换CDN');
			          document.getElementById('raw-markdown').style.display = 'block';
			          return;
			      }

			      // 1. 获取内容
			      const rawDiv = document.getElementById('raw-markdown');
			      if (!rawDiv) return;
			      const rawText = rawDiv.innerText;

			      // 2. 解析 Markdown
			      const renderer = new marked.Renderer();

			      renderer.code = function(code, language) {
			          if (language === 'echarts' || language === 'json') {
			              const id = 'chart_' + Math.random().toString(36).substr(2, 9);
			              // 使用 encodeURIComponent 保存原始代码串
			              return '<div id="' + id + '" class="chart-box" data-option="' + encodeURIComponent(code) + '"></div>';
			          }
			          return '<pre><code class="language-' + language + '">' + code + '</code></pre>';
			      };

			      document.getElementById('render-target').innerHTML = marked.parse(rawText, { renderer: renderer });

			      // 3. 渲染图表
			      if (typeof echarts !== 'undefined') {
			          document.querySelectorAll('.chart-box').forEach(box => {
			              try {
			                  // 解码数据
			                  const code = decodeURIComponent(box.getAttribute('data-option'));

			                  // 🌟 核心修改：使用 new Function 替代 JSON.parse
			                  // 这样可以兼容 LLM 生成的 JS 函数 (formatter: function()...)
			                  // 注意：这就要求 LLM 生成的是 JS 对象字面量，而不仅仅是 JSON (通常 LLM 都会这么做)
			                  const option = new Function('return ' + code)();

			                  const myChart = echarts.init(box);
			                  myChart.setOption(option);
			                  window.addEventListener('resize', () => myChart.resize());
			              } catch(e) {
			                  console.error('图表渲染失败', e);
			                  // 把具体的代码打印出来方便调试
			                  console.log('Error Code:', decodeURIComponent(box.getAttribute('data-option')));
			                  box.innerHTML = '<div style="color:red;padding:20px;text-align:center;border:1px dashed red;">' +
			                                  '<b>图表渲染错误</b><br/>' + e.message + '</div>';
			              }
			          });
			      }
			  };
			</script>
			</body>
			</html>
			""";

	// 生成html 报告的时候report-generator.txt的输出示例，对应里面的变量{json_example}，
	// 因为直接黏贴json示例到prompt需要对花括号进行转义
	// 但是有可能造成LLM生成的echarts代码直接带转义了， 所以通过变量引用传递给prompt的方式，避免转义
	public static final String cleanJsonExample = """
			{
			    "title": { "text": "月度销售额" },
			    "tooltip": { "trigger": "axis" },
			    "xAxis": { "type": "category", "data": ["1月", "2月"] },
			    "yAxis": { "type": "value" },
			    "series": [
			        { "type": "bar", "data": [120, 200] }
			    ]
			}""";

	/**
	 * 获取动态组装后的 Header CSS 中经常会出现百分号 %（例如 width: 100%;）所以用 .replace 而不是 String.format
	 */
	public String getHeader() {
		// 执行替换
		return REPORT_TEMPLATE_HEADER.replace("{{MARKED_URL}}", dataAgentProperties.getReportTemplate().getMarkedUrl())
			.replace("{{ECHARTS_URL}}", dataAgentProperties.getReportTemplate().getEchartsUrl());
	}

	/**
	 * 获取 Footer
	 */
	public String getFooter() {
		return REPORT_TEMPLATE_FOOTER;
	}

}
