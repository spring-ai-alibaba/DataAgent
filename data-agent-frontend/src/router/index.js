/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { createRouter, createWebHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import routes from '@/router/routes';
import modelConfigService from '@/services/modelConfig';

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 路由切换时滚动到顶部
    if (savedPosition) {
      return savedPosition;
    } else {
      return { top: 0 };
    }
  },
});

let hasShownWarning = false;

// 全局路由守卫
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  if (to.meta?.title) {
    document.title = `${to.meta.title} - Spring AI Alibaba Data Agent`;
  } else {
    document.title = 'Spring AI Alibaba Data Agent';
  }

  if (to.path === '/model-config') {
    console.log(`导航到: ${to.path} (${to.name})`);
    next();
    return;
  }

  // 检查模型配置是否就绪
  try {
    const result = await modelConfigService.checkReady();

    if (!result.ready) {
      // 模型配置未就绪，阻止跳转并重定向到配置页面
      const missingModels = [];
      if (!result.chatModelReady) {
        missingModels.push('聊天模型');
      }
      if (!result.embeddingModelReady) {
        missingModels.push('嵌入模型');
      }

      // 只在首次显示提示，避免重复提示
      if (!hasShownWarning) {
        ElMessage.warning({
          message: `欢迎使用！检测到您尚未配置${missingModels.join('和')}，请先配置 OpenAI/阿里/Ollama 等模型参数以激活系统。`,
          duration: 5000,
        });
        hasShownWarning = true;
      }

      console.log(`模型配置未就绪，重定向到配置页面`);
      next('/model-config');
      return;
    }

    // 模型配置就绪，重置提示标记
    hasShownWarning = false;
    console.log(`导航到: ${to.path} (${to.name})`);
    next();
  } catch (error) {
    console.error('检查模型配置失败:', error);

    // 如果检查失败，也重定向到配置页面
    if (!hasShownWarning) {
      ElMessage.error({
        message: '无法检查模型配置状态，请确保后端服务已启动并配置模型。',
        duration: 5000,
      });
      hasShownWarning = true;
    }

    next('/model-config');
  }
});

router.afterEach((to, from) => {
  // 路由切换后的处理
  console.log(`导航完成: ${to.path} ${from.path}`);
});

export default router;
