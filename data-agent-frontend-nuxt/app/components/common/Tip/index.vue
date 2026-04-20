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
	<v-snackbar
		v-model="isVisible"
		:color="color"
		:timeout="timeout"
		:location="location"
	>
		<div class="d-flex align-center w-100">
			<v-icon v-if="icon" :icon="icon" class="me-2"></v-icon>
			<div>
				{{ message }}
			</div>
		</div>

		<template #actions>
			<v-btn icon="mdi-close" variant="text" @click="isVisible = false"></v-btn>
		</template>
	</v-snackbar>
</template>

<script setup lang="ts">
/**
 * @description 全局提示消息 (Snackbar) 组件，用于展示 Store 中的提示信息
 */

import { useTipStore } from '~/stores/tips';
import { storeToRefs } from 'pinia';

// 从 store 中获取状态
const tipStore = useTipStore();
const { isVisible, message, color, timeout, location, icon } =
	storeToRefs(tipStore);
</script>
