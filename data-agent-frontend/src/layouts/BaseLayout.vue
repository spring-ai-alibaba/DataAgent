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
  <div class="base-layout">
    <!-- 现代化头部导航 -->
    <header class="page-header">
      <div class="header-content">
        <div class="brand-section">
          <div class="brand-logo">
            <img src="@/assets/logo.png" alt="logo" class="brand-logo-img" />
            <span class="brand-text">辅助交易AI数据分析</span>
          </div>
          <nav class="header-nav">
            <div class="nav-item" :class="{ active: isAgentPage() }" @click="goToAgentList">
              <i class="bi bi-grid-3x3-gap"></i>
              <span>智能体列表</span>
            </div>
            <div class="nav-item" :class="{ active: isModelConfigPage() }" @click="goToModelConfig">
              <i class="bi bi-gear"></i>
              <span>模型配置</span>
            </div>
          </nav>
        </div>
        <el-tooltip :content="themeTooltip" placement="bottom">
          <button
            type="button"
            class="theme-toggle"
            @click="cycleTheme"
            :aria-label="themeTooltip"
          >
            <i :class="themeIcon"></i>
          </button>
        </el-tooltip>
      </div>
    </header>

    <!-- 页面内容区域 -->
    <main class="page-content">
      <slot></slot>
    </main>
  </div>
</template>

<script>
  import { inject, ref, computed } from 'vue';
  import { useRouter } from 'vue-router';

  export default {
    name: 'BaseLayout',
    setup() {
      const router = useRouter();
      const toggleTheme = inject('toggleTheme', () => {});
      const setThemeMode = inject('setThemeMode', () => {});
      const themeMode = inject('themeMode', ref('system'));
      const isDark = inject('isDark', ref(false));

      const cycleTheme = () => {
        toggleTheme();
      };

      const themeIcon = computed(() => {
        if (themeMode.value === 'light') return 'bi bi-sun';
        if (themeMode.value === 'dark') return 'bi bi-moon-stars-fill';
        return 'bi bi-circle-half'; // system
      });

      const themeTooltip = computed(() => {
        if (themeMode.value === 'light') return '当前：亮色模式，点击切换到暗色';
        if (themeMode.value === 'dark') return '当前：暗色模式，点击跟随系统';
        return '当前：跟随系统，点击切换到亮色';
      });

      const goToAgentList = () => {
        router.push('/agents');
      };

      const goToModelConfig = () => {
        router.push('/model-config');
      };

      const isAgentPage = () => {
        return (
          router.currentRoute.value.name === 'AgentList' ||
          router.currentRoute.value.name === 'AgentDetail' ||
          router.currentRoute.value.name === 'AgentCreate' ||
          router.currentRoute.value.name === 'AgentRun'
        );
      };

      const isModelConfigPage = () => {
        return router.currentRoute.value.name === 'ModelConfig';
      };

      return {
        goToAgentList,
        goToModelConfig,
        isAgentPage,
        isModelConfigPage,
        cycleTheme,
        themeIcon,
        themeTooltip,
        isDark,
        themeMode,
      };
    },
  };
</script>

<style scoped>
  .base-layout {
    min-height: 100vh;
    background: var(--bg-layout);
  }

  .page-header {
    background: var(--bg-primary);
    border-bottom: 1px solid var(--border-primary);
    box-shadow: var(--shadow-sm);
    position: sticky;
    top: 0;
    z-index: 100;
    transition:
      background var(--transition-base),
      border-color var(--transition-base);
  }

  .header-content {
    width: 100%;
    padding: 0 1.5rem;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 4rem;
  }

  .brand-section {
    display: flex;
    align-items: center;
    gap: 2rem;
  }

  .brand-logo {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
  }

  .brand-logo-img {
    width: 1.5rem;
    height: 1.5rem;
    object-fit: contain;
  }

  .brand-logo i {
    font-size: 1.5rem;
    color: var(--primary-color);
  }

  .header-nav {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .nav-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s ease;
    color: var(--text-secondary);
    font-weight: 500;
  }

  .nav-item:hover {
    background: var(--bg-secondary);
    color: var(--text-primary);
  }

  .nav-item.active {
    background: var(--primary-light);
    color: var(--primary-color);
  }

  .nav-item i {
    font-size: 1rem;
  }

  .page-content {
    flex: 1;
    padding: 0;
  }

  .theme-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border: 1px solid var(--border-primary);
    border-radius: var(--radius-md);
    background: var(--bg-secondary);
    color: var(--text-secondary);
    cursor: pointer;
    transition: all var(--transition-base);
    flex-shrink: 0;
  }

  .theme-toggle:hover {
    background: var(--bg-tertiary);
    color: var(--text-primary);
    border-color: var(--border-secondary);
  }

  .theme-toggle i {
    font-size: 1rem;
  }
</style>
