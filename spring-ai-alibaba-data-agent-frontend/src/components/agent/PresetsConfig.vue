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
  <div class="agent-preset-questions">
    <div style="margin-bottom: 20px;">
      <h2>预设问题</h2>
      <p class="description">为智能体设置一些常见问题，用户可以直接点击使用</p>
    </div>
    <el-divider/>
    
    <!-- 顶部操作区：右上角添加按钮 -->
    <div class="header-actions">
      <span v-if="questions.length < 5" class="question-limit-tip">最多可添加 {{ 5 - questions.length }} 个问题</span>
      <el-button
        v-if="questions.length < 5"
        type="primary"
        :icon="Plus"
        @click="addQuestion"
        class="add-question-btn"
      >
        添加问题
      </el-button>
    </div>
    
    <!-- 问题列表 -->
    <div class="questions-container">
      <div 
        v-for="(question, index) in questions" 
        :key="index" 
        class="question-item"
        :class="{ 'drag-over': overIndex === index }"
        @dragover="onDragOver(index, $event)"
        @dragenter="onDragOver(index, $event)"
        @drop="onDrop(index)"
        @dragend="onDragEnd"
      >
        <div class="question-input-wrapper">
          <span
            class="drag-handle"
            title="拖动调整顺序"
            draggable="true"
            @dragstart="onDragStart(index, $event)"
          >
            <!-- 使用十字带箭头图标，通过纯CSS绘制，保证始终可见 -->
            <span class="drag-icon" aria-hidden="true"></span>
          </span>
          <el-input
            v-model="question.content"
            placeholder="请输入预设问题..."
            maxlength="100"
            show-word-limit
            class="question-input"
            clearable
          />
          <div class="question-actions">
            <!-- 删除按钮 -->
            <el-button
              v-if="questions.length > 1"
              type="danger"
              :icon="Delete"
              circle
              size="small"
              @click="removeQuestion(index)"
              class="remove-btn"
            />
          </div>
        </div>
      </div>
      
      
    </div>
    
    <!-- 保存按钮 -->
    <div class="save-actions">
      <el-button
        type="primary"
        :icon="Check"
        :loading="saving"
        @click="saveQuestions"
        size="large"
      >
        保存预设问题
      </el-button>
    </div>
  </div>
</template>

<script lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Check } from '@element-plus/icons-vue'
import { presetQuestionsApi } from '@/services/presetQuestions'

interface PresetQuestion {
  id?: number
  content: string
  order?: number
}

export default {
  name: 'AgentPresetQuestions',
  props: {
    agentId: {
      type: Number,
      required: true
    }
  },
  setup(props) {
    // 兜底校验，避免 undefined 触发请求
    const safeAgentId = () => {
      const id = Number(props.agentId)
      return Number.isFinite(id) && id > 0 ? id : NaN
    }
    const questions = ref<PresetQuestion[]>([
      { content: '' }
    ])
    const saving = ref(false)
    const loading = ref(false)
    const dragIndex = ref<number | null>(null)
    const overIndex = ref<number | null>(null)

    // 添加问题
    const addQuestion = () => {
      if (questions.value.length < 5) {
        questions.value.push({ content: '' })
      }
    }

    // 拖拽开始（仅通过拖拽图标触发）
    const onDragStart = (index: number, event: DragEvent) => {
      dragIndex.value = index
      if (event && event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(index))
      }
    }

    // 拖拽经过目标项
    const onDragOver = (index: number, event: DragEvent) => {
      event.preventDefault()
      overIndex.value = index
    }

    // 拖拽放下，完成交换
    const onDrop = (index: number) => {
      if (dragIndex.value === null || dragIndex.value === index) {
        overIndex.value = null
        dragIndex.value = null
        return
      }
      const from = dragIndex.value
      const to = index
      const list = questions.value.slice()
      const [moved] = list.splice(from, 1)
      list.splice(to, 0, moved)
      questions.value = list
      overIndex.value = null
      dragIndex.value = null
    }

    // 拖拽结束（清理状态）
    const onDragEnd = () => {
      overIndex.value = null
      dragIndex.value = null
    }

    // 删除问题
    const removeQuestion = async (index: number) => {
      try {
        await ElMessageBox.confirm(
          '确定要删除这个预设问题吗？',
          '确认删除',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
        
        questions.value.splice(index, 1)
        
        // 如果删除后没有问题了，添加一个空问题
        if (questions.value.length === 0) {
          questions.value.push({ content: '' })
        }
        
        ElMessage.success('删除成功')
      } catch (error) {
        // 用户取消删除
      }
    }

    // 保存问题
    const saveQuestions = async () => {
      // 验证问题内容
      const validQuestions = questions.value.filter(q => q.content.trim())
      
      if (validQuestions.length === 0) {
        ElMessage.warning('请至少添加一个预设问题')
        return
      }

      // 验证问题长度
      const invalidQuestions = validQuestions.filter(q => q.content.trim().length > 100)
      if (invalidQuestions.length > 0) {
        ElMessage.error('问题内容不能超过100个字符')
        return
      }

      try {
        saving.value = true
        
        const questionsToSave = validQuestions.map((q, index) => ({
          content: q.content.trim(),
          order: index + 1
        }))

        const id = safeAgentId()
        if (!Number.isFinite(id)) {
          ElMessage.error('无效的智能体ID，无法保存')
          return
        }
        const result = await presetQuestionsApi.saveQuestions(id, questionsToSave)
        
        ElMessage.success('保存成功')
        
        // 重新加载问题列表
        await loadQuestions()
      } catch (error) {
        console.error('保存预设问题失败:', error)
        ElMessage.error('保存失败，请重试')
      } finally {
        saving.value = false
      }
    }

    // 加载问题列表
    const loadQuestions = async () => {
      try {
        loading.value = true
        const id = safeAgentId()
        if (!Number.isFinite(id)) {
          // 无效ID则不请求
          questions.value = [{ content: '' }]
          return
        }
        const response = await presetQuestionsApi.getQuestions(id)

        if (response && response.length > 0) {
          // 确保前端字段命名统一
          questions.value = response.map((it) => ({
            id: it.id,
            content: it.content,
            order: it.order
          }))
        } else {
          // 如果没有问题，显示一个空的问题输入框
          questions.value = [{ content: '' }]
        }
      } catch (error) {
        console.error('加载预设问题失败:', error)
        ElMessage.error('加载预设问题失败')
        // 出错时显示一个空的问题输入框
        questions.value = [{ content: '' }]
      } finally {
        loading.value = false
      }
    }

    // 组件挂载时加载数据
    onMounted(() => {
      loadQuestions()
    })

    return {
      questions,
      saving,
      loading,
      addQuestion,
      removeQuestion,
      saveQuestions,
      Plus,
      Delete,
        Check,
        dragIndex,
        overIndex,
        onDragStart,
        onDragOver,
        onDrop,
        onDragEnd
    }
  }
}
</script>

<style scoped>
.agent-preset-questions {
  padding: 20px;
}

.description {
  color: #666;
  font-size: 14px;
  margin: 8px 0 0 0;
}

.questions-container {
  margin-bottom: 30px;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.question-item {
  margin-bottom: 16px;
}

.question-input-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-primary);
  border-radius: 6px;
  background: var(--bg-primary);
  cursor: grab;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.drag-handle:active {
  cursor: grabbing;
}

.drag-icon {
  display: inline-block;
  width: 16px;
  height: 16px;
  background-repeat: no-repeat;
  background-position: center;
  /* 十字带箭头 SVG 图标（灰色） */
  background-image: url("data:image/svg+xml;utf8,\
    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%239ca3af' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>\
      <path d='M12 2l3 3-3-3-3 3 3-3v20l-3-3 3 3 3-3-3 3'/>\
      <path d='M2 12l3-3-3 3 3 3-3-3h20l-3 3 3-3-3-3 3 3'/>\
    </svg>");
}

.question-input {
  flex: 1;
}

.question-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-item.drag-over {
  outline: 2px dashed var(--primary-color);
  border-radius: 8px;
}

.remove-btn {
  margin-top: 4px;
}

.add-question-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding: 16px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  background: #fafafa;
}

.add-question-btn {
  flex-shrink: 0;
}

.question-limit-tip {
  color: #999;
  font-size: 14px;
}

.save-actions {
  display: flex;
  justify-content: center;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .agent-preset-questions {
    padding: 16px;
  }
  
  .question-input-wrapper {
    flex-direction: column;
    gap: 8px;
  }
  
  .question-actions {
    align-self: flex-end;
  }
  
  .add-question-wrapper {
    flex-direction: column;
    align-items: stretch;
    text-align: center;
  }
}
</style>