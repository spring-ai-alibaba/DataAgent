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
  <BaseLayout>
    <el-container style="margin-top: 20px; gap: 10px;">
      <!-- 设置 header-->
      <el-header style="background-color: white; margin-bottom: 20px;">
        <el-row :gutter="20" align="middle">
          <el-col :span="1">
            <el-button type="primary" :icon="ArrowLeft" @click="goBack" circle style="transform: scale(1.2);"/>
          </el-col>
          <el-col :span="1" style="text-align: left;">
            <el-avatar :src="agent.avatar" size="large">{{ agent.name }}</el-avatar>
          </el-col>
          <el-col :span="30" style="text-align: left;">
            <h2>{{ agent.name }}</h2>
          </el-col>
        </el-row>
        <el-divider/>
      </el-header>
      <el-container style="gap: 10px;">
        <!-- 左侧菜单-->
        <el-aside width="200px" style="background-color: white;">
          <el-menu :default-active="activeMenuIndex" class="el-menu-vertical-demo" @select="handleMenuSelect">
            <el-menu-item-group title="基本信息">
              <el-menu-item index="basic">
                <el-icon><InfoFilled/></el-icon>
                基本信息
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="数据源配置">
              <el-menu-item index="datasource">
                <el-icon><Coin/></el-icon>
                数据源配置
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="PROMPT配置">
              <el-menu-item index="prompt">
                <el-icon><ChatLineSquare/></el-icon>
                自定义 PROMPT 配置
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="知识配置">
              <el-menu-item index="agent-knowledge">
                <el-icon><Document/></el-icon>
                智能体知识配置
              </el-menu-item>
              <el-menu-item index="business-knowledge">
                <el-icon><User/></el-icon>
                业务知识配置
              </el-menu-item>
              <el-menu-item index="semantic-model">
                <el-icon><Suitcase/></el-icon>
                语义模型配置
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="预设问题管理">
              <el-menu-item index="preset-questions">
                <el-icon><Setting/></el-icon>
                预设问题管理
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="运行与发布">
              <el-menu-item index="go-run">
                <el-icon><VideoPlay/></el-icon>
                前往运行页面
              </el-menu-item>
              <el-menu-item index="access-api">
                <el-icon><Connection/></el-icon>
                访问 API
              </el-menu-item>
            </el-menu-item-group>
          </el-menu>
        </el-aside>
        <el-main style="background-color: white;">
        <!-- 右侧内容-->
          <AgentBaseSetting v-if="activeMenuIndex === 'basic'" :agent="agent"></AgentBaseSetting>
          <AgentDataSourceConfig v-else-if="activeMenuIndex === 'datasource'" :agent-id="agent.id"></AgentDataSourceConfig>
          <AgentPromptConfig v-else-if="activeMenuIndex === 'prompt'" :agent-prompt="agent.prompt"></AgentPromptConfig>
          <BusinessKnowledgeConfig v-else-if="activeMenuIndex === 'business-knowledge'" :agent-id="agent.id"></BusinessKnowledgeConfig>
          <AgentSemanticsConfig v-else-if="activeMenuIndex === 'semantic-model'"></AgentSemanticsConfig>
          <AgentPresetsConfig v-else-if="activeMenuIndex === 'preset-questions'"></AgentPresetsConfig>
          <AgentAccessApi v-else-if="activeMenuIndex === 'access-api'"></AgentAccessApi>
          <AgentKnowledgeConfig v-else-if="activeMenuIndex === 'agent-knowledge'"></AgentKnowledgeConfig>
          <NotFound v-else></NotFound>
        </el-main>
      </el-container>
    </el-container>
  </BaseLayout>
</template>

<script lang="ts">
import { ref, defineComponent, Ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentService from '@/services/agent'
import { ArrowLeft, InfoFilled, Coin, ChatLineSquare, User, Suitcase, Setting, VideoPlay, Connection, Document } from '@element-plus/icons-vue'
import BaseLayout from '@/layouts/BaseLayout.vue'
import AgentBaseSetting from '@/components/agent/BaseSetting.vue'
import AgentPromptConfig from '@/components/agent/PromptConfig.vue'
import BusinessKnowledgeConfig from '@/components/agent/BusinessKnowledgeConfig.vue'
import AgentSemanticsConfig from "@/components/agent/SemanticsConfig.vue"
import AgentPresetsConfig from '@/components/agent/PresetsConfig.vue'
import AgentAccessApi from "@/components/agent/AccessApi.vue"
import AgentDataSourceConfig from '@/components/agent/DataSourceConfig.vue'
import AgentKnowledgeConfig from '@/components/agent/AgentKnowledgeConfig.vue'
import NotFound from "@/views/NotFound.vue";
import { Agent } from '@/services/agent'

export default defineComponent({
  name: 'AgentDetail',
  components: {
    BaseLayout,
    AgentBaseSetting,
    AgentPromptConfig,
    BusinessKnowledgeConfig,
    AgentSemanticsConfig,
    AgentPresetsConfig,
    AgentAccessApi,
    AgentDataSourceConfig,
    AgentKnowledgeConfig,
    NotFound,
    InfoFilled,
    Coin,
    ChatLineSquare,
    User,
    Suitcase,
    Setting,
    VideoPlay,
    Connection,
    Document
  },
  setup() {
    const router = useRouter()

    // 响应式数据
    const activeMenuIndex : Ref<string> = ref('basic')
    const agent : Ref<Agent> = ref({
      id: '',
      name: 'loading...',
      description: '',
      status: 'draft',
      createdAt: '',
      updatedAt: '',
      avatar: '',
      prompt: '',
      category: '',
      adminId: '',
      tags: '',
      humanReviewEnabled: false
    } as Agent)

    const handleMenuSelect = (index: string) => {
      const id = router.currentRoute.value.params.id
      activeMenuIndex.value = index
      if(index === 'go-run') {
        router.push(`/agent/${id}/run`)
      }
    }

    const goBack = () => {
      router.push('/agents')
    }

    const loadAgent = async () => {
      try {
        const id = router.currentRoute.value.params.id
        const loadAgent = await AgentService.get(id)
        if(loadAgent) {
          agent.value = loadAgent
        } else {
          throw new Error('Agent 不存在')
        }
      } catch (error) {
        ElMessage.error('加载失败')
        console.error('加载失败:', error)
      }
    }

    onMounted(async () => {
      await loadAgent()
    })

    return {
      ArrowLeft,
      agent,
      activeMenuIndex,
      handleMenuSelect,
      goBack
    }
  }
})
</script>

<style scoped>

</style>
