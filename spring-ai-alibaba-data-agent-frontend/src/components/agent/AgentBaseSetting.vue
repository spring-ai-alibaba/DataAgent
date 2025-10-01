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
    <div class="agent-base-setting">
      <div style="margin-bottom: 20px;">
        <h2>基本信息</h2>
      </div>
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="form-item">
            <label>智能体名称</label>
            <el-input v-model="props.agent.name" placeholder="请输入智能体名称" size="large"/>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="form-item">
            <label>分类</label>
            <el-input v-model="props.agent.category" placeholder="请输入智能体分类" size="large"/>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="24">
          <div class="form-item">
            <label>描述</label>
            <el-input
                v-model="props.agent.description"
                :rows="4"
                type="textarea"
                placeholder="请输入智能体描述"
                size="large"
            />
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="24">
          <div class="form-item">
            <label>Prompt</label>
            <el-input
                v-model="props.agent.prompt"
                :rows="4"
                type="textarea"
                placeholder="请输入智能体Prompt"
                size="large"
            />
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <div class="form-item">
            <label>标签</label>
            <el-input v-model="props.agent.tags" placeholder="多个标签用逗号分隔" size="large"/>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="form-item">
            <label>状态</label>
            <el-select v-model="props.agent.status" placeholder="请选择状态" style="width: 100%" size="large">
              <el-option key="draft" label="待发布" value="draft"/>
              <el-option key="published" label="已发布" value="published"/>
              <el-option key="offline" label="已下线" value="offline"/>
            </el-select>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <div class="form-item">
            <label>创建时间</label>
            <el-input disabled :model-value="formattedCreateTime" size="large"></el-input>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="form-item">
            <label>更新时间</label>
            <el-input disabled :model-value="formattedUpdateTime" size="large"></el-input>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="24">
          <div class="form-item form-switch">
            <el-switch v-model="props.agent.humanReviewEnabled" size="large"/>
            <span>启用计划人工复核</span>
            <el-text class="mx-1">（开启后，Planner 计划会在执行前等待人工复核）</el-text>
          </div>
        </el-col>
      </el-row>

      <el-button type="primary" :icon="Edit" round @click="updateAgent" size="large">保存</el-button>
    </div>
</template>

<script lang="ts">
import { Agent } from '@/services/agent'
import agentService from "@/services/agent"
import { defineComponent, computed } from "vue"
import { ElMessage } from 'element-plus'
import { Edit } from '@element-plus/icons-vue'

export default defineComponent({
  name: 'AgentBaseSetting',
  props: {
    agent: {
      type: Object as () => Agent,
      required: true
    }
  },
  setup(props) {

    const updateAgent = async () => {
      try {
        const agent = await agentService.update(props.agent.id, props.agent)
        if(agent === null) {
          console.error('更新智能体失败:', agent)
          ElMessage.error('更新失败：未知错误')
        } else {
          ElMessage.success('更新成功！')
          props.agent = agent;
        }
      } catch (e) {
        console.error('更新智能体失败:', e)
        ElMessage.error('更新失败：' + (e.message || '未知错误'))
      }
    }

    const formatDateTime = (dateString) => {
      if (!dateString) return '-'
      const date = new Date(dateString)
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    }

    // 创建计算属性
    const formattedCreateTime = computed(() => formatDateTime(props.agent.createTime))
    const formattedUpdateTime = computed(() => formatDateTime(props.agent.updateTime))

    return {
      Edit,
      props,
      updateAgent,
      formattedCreateTime,
      formattedUpdateTime
    }
  }
})
</script>

<style scoped>
.agent-base-setting {
  padding: 20px;
}

.form-item {
  margin-bottom: 25px;
}

.form-item label {
  display: block;
  margin-bottom: 10px;
  font-weight: 500;
  font-size: 15px;
}

.form-switch {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
}

</style>
