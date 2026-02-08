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

<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { Tools } from '@element-plus/icons-vue';
  import MarkdownAgentContainer from './markdown/MarkdownAgentContainer.vue';

  interface ToolCall {
    toolName: string;
    input: any;
    output: any;
  }

  interface ResultData {
    toolCalls?: ToolCall[];
    [key: string]: any;
  }

  const props = defineProps<{
    resultData: ResultData;
  }>();

  const activeNames = ref<string[]>([]);

  const toolCalls = computed(() => {
    const data = props.resultData;
    if (!data) return [];

    if (Array.isArray(data.toolCalls)) {
      return data.toolCalls;
    }

    if (Array.isArray(data)) {
      return data;
    }

    if (data.toolName && data.input !== undefined && data.output !== undefined) {
      return [data];
    }

    return [];
  });

  const isSequentialThinking = (toolName: string): boolean => {
    return toolName === 'sequential_thinking';
  };

  const formatJson = (data: any): string => {
    if (typeof data === 'string') {
      try {
        return JSON.stringify(JSON.parse(data), null, 2);
      } catch {
        return data;
      }
    }
    return JSON.stringify(data, null, 2);
  };

  const getThoughtContent = (input: any): string => {
    if (typeof input === 'string') {
      try {
        const parsed = JSON.parse(input);
        return parsed.thought || '';
      } catch {
        return '';
      }
    }
    return input?.thought || '';
  };

  const getThoughtMetadata = (input: any): any => {
    if (typeof input === 'string') {
      try {
        const parsed = JSON.parse(input);
        return {
          thought_number: parsed.thought_number,
          total_thoughts: parsed.total_thoughts,
          next_thought_needed: parsed.next_thought_needed,
          needs_more_thoughts: parsed.needs_more_thoughts,
        };
      } catch {
        return null;
      }
    }
    return {
      thought_number: input?.thought_number,
      total_thoughts: input?.total_thoughts,
      next_thought_needed: input?.next_thought_needed,
      needs_more_thoughts: input?.needs_more_thoughts,
    };
  };

  const processOutputContent = (content: any): string => {
    if (typeof content === 'string') {
      // 检查是否是双引号包裹的字符串
      if (content.startsWith('"') && content.endsWith('"')) {
        try {
          // 尝试使用 JSON.parse 解析
          return JSON.parse(content);
        } catch {
          // 如果解析失败，去掉首尾的双引号
          return content.substring(1, content.length - 1);
        }
      }
    }
    return content || '';
  };
</script>

<template>
  <div v-if="toolCalls.length > 0" class="tool-calling-container">
    <el-collapse v-model="activeNames" class="tool-collapse">
      <el-collapse-item
        v-for="(toolCall, index) in toolCalls"
        :key="index"
        :name="String(index)"
        class="tool-collapse-item"
      >
        <template #title>
          <div class="tool-title">
            <el-icon class="tool-icon"><Tools /></el-icon>
            <span class="tool-name">调用工具 {{ toolCall.toolName }}</span>
          </div>
        </template>

        <div class="tool-content">
          <div class="content-section">
            <div class="section-title">输入参数 (Input)</div>
            <div v-if="isSequentialThinking(toolCall.toolName)" class="sequential-thinking-content">
              <div v-if="getThoughtMetadata(toolCall.input)" class="thought-metadata">
                <span class="metadata-item">
                  思考步骤: {{ getThoughtMetadata(toolCall.input)?.thought_number }} /
                  {{ getThoughtMetadata(toolCall.input)?.total_thoughts }}
                </span>
                <span
                  class="metadata-item"
                  :class="{
                    'status-true': getThoughtMetadata(toolCall.input)?.next_thought_needed,
                    'status-false': !getThoughtMetadata(toolCall.input)?.next_thought_needed,
                  }"
                >
                  {{
                    getThoughtMetadata(toolCall.input)?.next_thought_needed
                      ? '需要继续思考'
                      : '思考完成'
                  }}
                </span>
              </div>
              <div class="thought-content">
                <MarkdownAgentContainer :content="getThoughtContent(toolCall.input)" />
              </div>
            </div>
            <pre v-else class="json-display">{{ formatJson(toolCall.input) }}</pre>
          </div>

          <div class="content-section">
            <div class="section-title">输出参数 (Output)</div>
            <div class="markdown-content">
              <MarkdownAgentContainer :content="processOutputContent(toolCall.output)" />
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
  .tool-calling-container {
    width: 100%;
    margin: 0px 0;
  }

  .tool-collapse {
    border-radius: 4px;
    overflow: hidden;
  }

  .tool-collapse-item {
  }

  .tool-collapse-item:last-child {
  }

  .tool-title {
    display: flex;
    align-items: center;
    width: 100%;
  }

  .tool-icon {
    margin-left: 8px;
    margin-right: 8px;
    color: #409eff;
  }

  .tool-name {
    font-weight: 500;
    color: #303133;
    font-size: 14px;
  }

  .tool-content {
    padding: 16px;
  }

  .content-section {
    margin-bottom: 16px;
  }

  .content-section:last-child {
    margin-bottom: 0;
  }

  .section-title {
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 8px;
    padding-bottom: 4px;
  }

  .json-display {
    background-color: #f5f7fa;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    padding: 12px;
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    color: #303133;
    overflow-x: auto;
    overflow-y: auto;
    white-space: pre-wrap;
    word-wrap: break-word;
    max-height: 100px;
  }

  .sequential-thinking-content {
    background-color: #f5f7fa;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    padding: 12px;
  }

  .thought-metadata {
    display: flex;
    gap: 12px;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #e4e7ed;
  }

  .metadata-item {
    font-size: 12px;
    color: #606266;
    padding: 2px 8px;
    background-color: #e4e7ed;
    border-radius: 3px;
  }

  .status-true {
    background-color: #ecf5ff;
    color: #409eff;
  }

  .status-false {
    background-color: #f0f9ff;
    color: #67c23a;
  }

  .thought-content {
    padding: 8px;
    background-color: #ffffff;
    border-radius: 4px;
    min-height: 40px;
  }

  .markdown-content {
    background-color: #f5f7fa;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    padding: 12px;
    min-height: 40px;
  }
</style>
