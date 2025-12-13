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
  <div class="human-feedback-area">
    <div class="feedback-header">
      <el-icon><ChatDotRound /></el-icon>
      <span>请对智能体的计划进行评价</span>
    </div>
    <div class="feedback-input">
      <el-input
        v-model="feedbackInput"
        type="textarea"
        :rows="3"
        placeholder="请输入您的反馈意见（可选）..."
        maxlength="500"
        show-word-limit
      />
    </div>
    <div class="feedback-actions">
      <el-button type="success" @click="submitFeedback(false)">
        <el-icon><Check /></el-icon>
        通过计划
      </el-button>
      <el-button type="danger" @click="submitFeedback(true)">
        <el-icon><Close /></el-icon>
        不通过计划
      </el-button>
    </div>
  </div>
</template>

<script lang="ts">
  import { ref, defineComponent, PropType } from 'vue';
  import { type GraphRequest } from '../../services/graph';
  import { ChatDotRound, Check, Close } from '@element-plus/icons-vue';

  export default defineComponent({
    name: 'HumanFeedback',
    components: {
      ChatDotRound,
      Check,
      Close,
    },
    props: {
      request: {
        type: Object as PropType<GraphRequest>,
        required: true,
      },
      handleFeedback: {
        type: Function as PropType<
          (request: GraphRequest, rejectedPlan: boolean, content: string) => Promise<void>
        >,
        required: true,
      },
    },
    setup(props) {
      const feedbackInput = ref('');

      const submitFeedback = (rejectedPlan: boolean) => {
        // 准备反馈内容，如果用户输入为空则使用默认值
        const feedbackContent = feedbackInput.value.trim() || 'Accept';
        props.handleFeedback(props.request, rejectedPlan, feedbackContent);
        // 重置输入
        feedbackInput.value = '';
      };

      return {
        feedbackInput,
        submitFeedback,
      };
    },
  });
</script>

<style scoped>
  .human-feedback-area {
    background: #f8fbff;
    border: 1px solid #e1f0ff;
    border-radius: 12px;
    padding: 20px;
    margin: 16px 0;
  }

  .feedback-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
    color: #409eff;
    font-size: 16px;
    font-weight: 500;
  }

  .feedback-header .el-icon {
    color: #409eff;
    font-size: 18px;
  }

  .feedback-input {
    margin-bottom: 16px;
  }

  .feedback-actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .feedback-actions {
      flex-direction: column;
    }

    .feedback-actions .el-button {
      width: 100%;
    }
  }
</style>
