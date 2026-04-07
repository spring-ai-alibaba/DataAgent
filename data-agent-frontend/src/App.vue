<!--
  ~ Copyright 2024-2025 the original author or authors.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~      https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
  import { ref, readonly, provide, onMounted, onUnmounted, computed } from 'vue';

  // 'light' | 'dark' | 'system'
  const themeMode = ref('system');
  const isDark = ref(false);

  let mediaQuery = null;

  function applyTheme(dark) {
    isDark.value = dark;
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  }

  function resolveAndApply() {
    if (themeMode.value === 'system') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      applyTheme(mq.matches);
    } else {
      applyTheme(themeMode.value === 'dark');
    }
  }

  function handleSystemChange(e) {
    if (themeMode.value === 'system') {
      applyTheme(e.matches);
    }
  }

  function setThemeMode(mode) {
    themeMode.value = mode;
    localStorage.setItem('themeMode', mode);
    resolveAndApply();
  }

  // Cycle: light → dark → system → light
  function toggleTheme() {
    const next = themeMode.value === 'light' ? 'dark' : themeMode.value === 'dark' ? 'system' : 'light';
    setThemeMode(next);
  }

  // Initialize from storage
  const saved = localStorage.getItem('themeMode');
  if (saved && ['light', 'dark', 'system'].includes(saved)) {
    themeMode.value = saved;
  } else {
    // Legacy migration: old key was 'theme'
    const legacy = localStorage.getItem('theme');
    if (legacy === 'dark') themeMode.value = 'dark';
    else if (legacy === 'light') themeMode.value = 'light';
    else themeMode.value = 'system';
  }
  resolveAndApply();

  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

  onMounted(() => {
    mediaQuery.addEventListener('change', handleSystemChange);
  });

  onUnmounted(() => {
    mediaQuery?.removeEventListener('change', handleSystemChange);
  });

  provide('toggleTheme', toggleTheme);
  provide('setThemeMode', setThemeMode);
  provide('themeMode', readonly(themeMode));
  provide('isDark', readonly(isDark));
</script>

<style>
  #app {
    min-height: 100vh;
  }
</style>
