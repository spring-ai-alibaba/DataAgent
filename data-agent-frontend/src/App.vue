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
import { ref, provide, onMounted, onUnmounted } from 'vue';

const isDark = ref(false);

let mediaQuery = null;

function applyTheme(dark) {
  isDark.value = dark;
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
}

function handleSystemChange(e) {
  // 只有用户未手动设置时才跟随系统
  if (!localStorage.getItem('theme')) {
    applyTheme(e.matches);
  }
}

function toggleTheme() {
  const next = !isDark.value;
  localStorage.setItem('theme', next ? 'dark' : 'light');
  applyTheme(next);
}

onMounted(() => {
  const saved = localStorage.getItem('theme');
  if (saved) {
    applyTheme(saved === 'dark');
  } else {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    applyTheme(mediaQuery.matches);
    mediaQuery.addEventListener('change', handleSystemChange);
  }
});

onUnmounted(() => {
  mediaQuery?.removeEventListener('change', handleSystemChange);
});

provide('toggleTheme', toggleTheme);
provide('isDark', isDark);
</script>

<style>
  #app {
    min-height: 100vh;
  }
</style>
