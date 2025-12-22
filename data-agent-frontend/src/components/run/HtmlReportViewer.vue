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
  <div class="html-report-container" :class="{ 'streaming-report': isStreaming }">
    <div class="html-report-header">
      <div class="report-info">
        <el-icon><Document /></el-icon>
        <span>{{ headerTitle }}</span>
      </div>
      <el-button
        v-if="showDownloadButton"
        type="primary"
        size="small"
        @click="handleDownload"
      >
        <el-icon><Download /></el-icon>
        下载报告
      </el-button>
    </div>
    <iframe
      :ref="setIframeRef"
      :srcdoc="sanitizedHtml"
      class="html-report-iframe"
      frameborder="0"
      scrolling="no"
      sandbox="allow-same-origin"
      @load="onIframeLoad"
    ></iframe>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, nextTick, PropType } from 'vue';
import { Document, Download } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import DOMPurify from 'dompurify';

export default defineComponent({
  name: 'HtmlReportViewer',
  components: {
    Document,
    Download,
  },
  props: {
    htmlContent: {
      type: String,
      required: true,
    },
    isStreaming: {
      type: Boolean,
      default: false,
    },
    showDownloadButton: {
      type: Boolean,
      default: true,
    },
    reportId: {
      type: String,
      default: () => `report-${Date.now()}`,
    },
  },
  setup(props) {
    const iframeRef = ref<HTMLIFrameElement | null>(null);

    const headerTitle = computed(() => {
      return props.isStreaming ? 'HTML报告预览（正在生成中...）' : 'HTML报告';
    });

    // HTML内容安全处理
    const sanitizedHtml = computed(() => {
      if (!props.htmlContent) return '';

      let html = props.htmlContent;

      // 移除可能的标记符号
      html = html.replace(/^\$\$\$html/, '').replace(/\$\$\$\/html$/, '').trim();

      // 如果HTML不包含完整的文档结构，包装成完整HTML
      if (!html.includes('<!DOCTYPE') && !html.includes('<html')) {
        html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
${html}
</body>
</html>`;
      }

      // 使用DOMPurify进行安全过滤
      return DOMPurify.sanitize(html, {
        WHOLE_DOCUMENT: true,
        RETURN_DOM: false,
        RETURN_DOM_FRAGMENT: false,
        FORCE_BODY: false,
        KEEP_CONTENT: true,
        ALLOW_DATA_ATTR: true,
        ALLOW_UNKNOWN_PROTOCOLS: false,
        ADD_TAGS: [
          'style',
          'link',
          'meta',
          'script',
          'svg',
          'path',
          'circle',
          'rect',
          'line',
          'polyline',
          'polygon',
          'g',
          'defs',
          'clipPath',
          'text',
          'tspan',
          'canvas',
          'use',
          'symbol',
        ],
        ADD_ATTR: [
          'style',
          'class',
          'id',
          'data-*',
          'viewBox',
          'd',
          'cx',
          'cy',
          'r',
          'x',
          'y',
          'x1',
          'y1',
          'x2',
          'y2',
          'width',
          'height',
          'points',
          'fill',
          'stroke',
          'stroke-width',
          'stroke-linecap',
          'stroke-linejoin',
          'transform',
          'xmlns',
          'xmlns:xlink',
          'xlink:href',
          'opacity',
          'fill-opacity',
          'stroke-opacity',
        ],
      });
    });

    const setIframeRef = (el: any) => {
      if (el && el instanceof HTMLIFrameElement) {
        iframeRef.value = el;
      }
    };

    const onIframeLoad = () => {
      nextTick(() => {
        const iframe = iframeRef.value;
        if (iframe && iframe.contentWindow) {
          try {
            const iframeDoc = iframe.contentWindow.document;
            const body = iframeDoc.body;
            const html = iframeDoc.documentElement;

            // 获取内容高度
            const height = Math.max(
              body.scrollHeight,
              body.offsetHeight,
              html.clientHeight,
              html.scrollHeight,
              html.offsetHeight
            );

            // 设置iframe高度，但不超过最大高度
            const maxHeight = window.innerHeight * 0.8;
            iframe.style.height = Math.min(height + 20, maxHeight) + 'px';
          } catch (e) {
            console.warn('无法获取iframe内容高度:', e);
          }
        }
      });
    };

    const handleDownload = () => {
      if (!props.htmlContent) {
        ElMessage.warning('没有可下载的HTML报告');
        return;
      }

      let content = props.htmlContent;

      // 去除可能的Markdown前后缀
      if (content.startsWith('```html')) {
        content = content.substring(7);
      }
      if (content.endsWith('```')) {
        content = content.substring(0, content.length - 3);
      }

      const blob = new Blob([content], { type: 'text/html' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `report_${new Date().getTime()}.html`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      ElMessage.success('HTML报告下载成功');
    };

    return {
      iframeRef,
      headerTitle,
      sanitizedHtml,
      setIframeRef,
      onIframeLoad,
      handleDownload,
    };
  },
});
</script>

<style scoped>
.html-report-container {
  width: 100%;
  max-width: 100%;
  margin: 16px 0;
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
}

.streaming-report {
  margin: 0 !important;
  border-radius: 8px;
}

.streaming-report .html-report-header {
  background: #f0f7ff;
}

.streaming-report .report-info span {
  color: #409eff !important;
  font-weight: 500;
  font-size: 14px;
}

.html-report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8fbff;
  border-bottom: 1px solid #e1f0ff;
}

.report-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
  font-size: 16px;
  font-weight: 500;
}

.html-report-iframe {
  width: 100%;
  min-height: 600px;
  max-height: 80vh;
  border: none;
  display: block;
  background: white;
  overflow: hidden;
}
</style>

