<script setup lang="ts">
import type { NuxtError } from '#app';

const props = defineProps({
	error: Object as () => NuxtError,
});

const statusCode = computed(() => props.error?.statusCode || 500);
const statusText = computed(() => {
	const map: Record<number, string> = {
		400: '请求参数错误',
		401: '身份认证失败',
		403: '没有访问权限',
		404: '页面不存在',
		500: '服务器内部错误',
		502: '网关错误',
		503: '服务暂时不可用',
	};
	return props.error?.statusMessage || map[statusCode.value] || '发生了未知错误';
});

const illustration = computed(() => {
	if (statusCode.value === 404) return 'mdi-map-marker-question-outline';
	if (statusCode.value === 403) return 'mdi-shield-lock-outline';
	if (statusCode.value === 401) return 'mdi-account-lock-outline';
	return 'mdi-alert-circle-outline';
});

const handleBack = () => clearError({ redirect: '/' });
</script>

<template>
	<div class="error-page">
		<div class="error-card">
			<div class="error-icon-wrap">
				<v-icon :icon="illustration" size="72" color="#94a3b8" />
			</div>

			<div class="error-code">{{ statusCode }}</div>
			<div class="error-text">{{ statusText }}</div>

			<p v-if="error?.message && error.message !== statusText" class="error-detail">
				{{ error.message }}
			</p>

			<div class="error-actions">
				<v-btn
					color="primary"
					variant="flat"
					size="large"
					prepend-icon="mdi-home-outline"
					class="action-btn"
					@click="handleBack"
				>
					返回首页
				</v-btn>
				<v-btn
					variant="outlined"
					size="large"
					prepend-icon="mdi-refresh"
					class="action-btn"
					@click="() => reloadNuxtApp()"
				>
					刷新页面
				</v-btn>
			</div>
		</div>
	</div>
</template>

<style scoped>
.error-page {
	min-height: 100vh;
	display: flex;
	align-items: center;
	justify-content: center;
	background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
	padding: 24px;
}

.error-card {
	text-align: center;
	max-width: 460px;
	width: 100%;
}

.error-icon-wrap {
	margin-bottom: 20px;
	opacity: 0.6;
}

.error-code {
	font-size: 96px;
	font-weight: 800;
	line-height: 1;
	color: #cbd5e1;
	letter-spacing: -4px;
	margin-bottom: 12px;
}

.error-text {
	font-size: 20px;
	font-weight: 600;
	color: #334155;
	margin-bottom: 8px;
}

.error-detail {
	font-size: 14px;
	color: #94a3b8;
	line-height: 1.6;
	margin-bottom: 32px;
	word-break: break-word;
}

.error-actions {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12px;
	margin-top: 28px;
}

.action-btn {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-weight: 600 !important;
	border-radius: 12px !important;
	padding: 0 24px !important;
}
</style>
