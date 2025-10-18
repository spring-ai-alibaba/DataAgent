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
    <div class="agent-preset-questions-page">
      <AgentPresetQuestions :agent-id="agentId" />
    </div>
  </BaseLayout>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BaseLayout from '@/layouts/BaseLayout.vue'
import AgentPresetQuestions from '@/components/agent/AgentPresetQuestions.vue'

export default {
  name: 'AgentPresetQuestionsPage',
  components: {
    BaseLayout,
    AgentPresetQuestions
  },
  setup() {
    const route = useRoute()
    const agentId = ref(null)

    onMounted(() => {
      // 从路由参数获取智能体ID
      agentId.value = parseInt(route.params.id)
      
      if (!agentId.value || isNaN(agentId.value)) {
        console.error('无效的智能体ID')
      }
    })

    return {
      agentId
    }
  }
}
</script>

<style scoped>
.agent-preset-questions-page {
  min-height: 100vh;
  background: #f8fafc;
}
</style>
