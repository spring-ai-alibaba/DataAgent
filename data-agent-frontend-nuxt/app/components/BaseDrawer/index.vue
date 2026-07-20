/*
 * Copyright 2026 the original author or authors.
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
 */

<template>
	<div class="base-drawer" :style="cssVars">
		<aside
			class="base-drawer__left"
			:class="{ 'base-drawer__left--closed': !modelValue }"
		>
			<slot name="drawer" />
		</aside>

		<section class="base-drawer__right">
			<header v-if="$slots.header" class="base-drawer__header">
				<slot name="header" :toggle="toggle" :is-open="modelValue" />
			</header>
			<div class="base-drawer__content">
				<slot />
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
/**
 * @description 基础侧边栏布局组件，提供左侧抽屉和右侧内容区的响应式布局
 */

interface Props {
	/** 抽屉宽度 (数字或带单位的字符串) */
	drawerWidth?: number | string;
	/** 抽屉是否展开 (支持 v-model) */
	modelValue?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
	drawerWidth: 260,
	modelValue: true,
});

const emit = defineEmits<{
	/** 更新展开状态 */
	'update:modelValue': [value: boolean];
}>();

/**
 * @description 切换抽屉展开/折叠状态
 */
const toggle = () => {
	emit('update:modelValue', !props.modelValue);
};

const normalizedDrawerWidth = computed(() => {
	const width = props.drawerWidth;
	return typeof width === 'number' ? `${width}px` : width;
});

const cssVars = computed(() => ({
	'--drawer-width': normalizedDrawerWidth.value,
}));
</script>

<style scoped>
.base-drawer {
	display: flex;
	width: 100%;
	height: 100vh;
	overflow: hidden;
	background: transparent;
}

.base-drawer__left {
	width: var(--drawer-width);
	height: 100%;
	background: rgb(249 239 223 / 94%);
	color: var(--domus-ink);
	transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
	overflow: hidden;
	display: flex;
	flex-direction: column;
	border-right: 1px solid var(--domus-line);
	flex-shrink: 0;
	white-space: nowrap;
}

.base-drawer__left--closed {
	width: 0;
}

.base-drawer__right {
	flex: 1;
	display: flex;
	flex-direction: column;
	min-width: 0; /* Prevent flex child overflow */
	height: 100%;
}

.base-drawer__header {
	height: 64px;
	border-bottom: 1px solid var(--domus-line);
	background: rgb(244 234 220 / 90%);
	backdrop-filter: blur(16px);
	display: flex;
	align-items: center;
	padding: 0 16px;
	flex-shrink: 0;
}

.base-drawer__content {
	flex: 1;
	overflow: auto;
	position: relative;
}

@media (max-width: 900px) {
	.base-drawer__left {
		position: absolute;
		inset: 0 auto 0 0;
		z-index: 50;
		box-shadow: 18px 0 50px rgb(32 24 18 / 18%);
	}

	.base-drawer__left--closed {
		box-shadow: none;
	}
}
</style>
