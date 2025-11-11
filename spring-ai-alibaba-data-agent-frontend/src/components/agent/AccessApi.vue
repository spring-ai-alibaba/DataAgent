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
  <!-- todo: 后端开发完成后，补充前端页面 -->
  <div style="max-width: 600px; margin: 40px auto 0">
    <el-alert
      title="相关功能正在开发中，请耐心等待..."
      type="primary"
      :closable="false"
      show-icon
    />
  </div>
</template>

<script lang="ts">
  import { defineComponent } from 'vue';

  export default defineComponent({
    name: 'AgentAccessApi',
    setup() {
      return {};
    },
  });
</script>

<style scoped></style>
