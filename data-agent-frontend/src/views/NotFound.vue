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
  <div>
    <el-result icon="warning" title="404" sub-title="抱歉，您访问的页面不存在或已被移除。">
      <template #extra>
        <el-button type="primary" @click="goHome">
          <el-icon><House /></el-icon>
          返回首页
        </el-button>
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回上页
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script>
  import { useRouter } from 'vue-router';
  import { House, ArrowLeft } from '@element-plus/icons-vue';

  export default {
    name: 'NotFound',
    components: {
      House,
      ArrowLeft,
    },
    setup() {
      const router = useRouter();

      const goBack = () => {
        if (window.history.length > 1) {
          router.go(-1);
        } else {
          goHome();
        }
      };

      const goHome = () => {
        router.push('/agents');
      };

      return {
        goBack,
        goHome,
      };
    },
  };
</script>

<style scoped></style>
