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
