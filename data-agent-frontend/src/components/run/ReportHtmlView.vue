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
  <div class="report-html-view-wrapper">
    <iframe
      ref="iframeRef"
      class="report-html-iframe"
      sandbox="allow-scripts"
      title="HTML报告预览"
    />
  </div>
</template>

<script lang="ts">
  import { defineComponent, ref, watch, nextTick } from 'vue';
  import { buildReportHtml } from './charts/report-html-template';

  export default defineComponent({
    name: 'ReportHtmlView',
    props: {
      content: {
        type: String,
        default: '',
      },
    },
    setup(props) {
      const iframeRef = ref<HTMLIFrameElement | null>(null);

      const loadHtml = () => {
        if (!iframeRef.value) return;

        if (!props.content) {
          iframeRef.value.srcdoc =
            '<html><body style="padding:20px;color:#666;">暂无报告内容</body></html>';
          return;
        }

        const html = buildReportHtml(props.content);
        const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
        const url = URL.createObjectURL(blob);

        const iframe = iframeRef.value;
        const onLoad = () => {
          URL.revokeObjectURL(url);
          iframe.removeEventListener('load', onLoad);
        };
        iframe.addEventListener('load', onLoad);
        iframe.src = url;
      };

      watch(
        () => props.content,
        () => {
          nextTick(loadHtml);
        },
        { immediate: true },
      );

      return {
        iframeRef,
      };
    },
  });
</script>

<style scoped>
  .report-html-view-wrapper {
    width: 100%;
    min-height: 400px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    overflow: hidden;
    background: #fff;
  }

  .report-html-iframe {
    width: 100%;
    min-height: 600px;
    border: none;
    display: block;
  }
</style>
