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
	<header class="d-flex align-center justify-space-between mb-8 knowledge-header">
		<div>
			<h1 class="text-h4 font-weight-bold mb-1 knowledge-header__title">{{ title }}</h1>
			<p class="text-body-2 text-medium-emphasis">{{ subtitle }}</p>
		</div>
		<div class="d-flex ga-3">
			<slot name="actions" />
		</div>
	</header>
</template>

<script setup lang="ts">
defineProps<{
	title: string;
	subtitle: string;
}>();
</script>

<style scoped>
.knowledge-header__title {
	color: #1565c0;
}
</style>
