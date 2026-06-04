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
	<div class="welcome-wrap">
		<!-- Agent Avatar -->
		<div class="agent-avatar-wrap">
			<v-avatar
				:image="store.currentAgentAvatar || undefined"
				:color="store.currentAgentAvatar ? undefined : 'grey-lighten-3'"
				size="80"
				rounded="xl"
				class="agent-avatar"
			>
				<span v-if="!store.currentAgentAvatar" class="agent-avatar-emoji">🤖</span>
			</v-avatar>
		</div>

		<!-- Agent Name -->
		<h2 class="welcome-title">
			您好，我是 <span class="agent-name">{{ store.currentAgentName || '数据助手' }}</span>
		</h2>

		<!-- Agent Description -->
		<p class="welcome-desc">
			{{ store.currentAgentDescription || '我可以为您分析数据库中的表结构、生成 SQL 或可视化图表。' }}
		</p>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';
const store = useChatStore();
</script>

<style scoped>
.welcome-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 1;
	padding: 40px 24px;
	text-align: center;
}

.agent-avatar-wrap {
	margin-bottom: 24px;
}

.agent-avatar {
	box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.agent-avatar-emoji {
	font-size: 36px;
	line-height: 1;
}

.welcome-title {
	font-size: 26px;
	font-weight: 700;
	color: #0f172a;
	margin-bottom: 12px;
	letter-spacing: -0.3px;
}

.agent-name {
	color: #1e293b;
}

.welcome-desc {
	font-size: 14.5px;
	color: #64748b;
	max-width: 480px;
	line-height: 1.7;
}
</style>
