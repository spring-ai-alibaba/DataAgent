<!--
 Copyright 2026 the original author or authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<template>
	<div class="welcome-wrap">
		<p class="welcome-eyebrow">BUSINESS WORK, NOT TOOL LEARNING</p>
		<div class="agent-avatar-wrap">
			<v-avatar
				:image="store.currentAgentAvatar || undefined"
				:color="store.currentAgentAvatar ? undefined : 'transparent'"
				size="84"
				rounded="lg"
				class="agent-avatar"
			>
				<img
					v-if="!store.currentAgentAvatar"
					src="/logo.png"
					alt="Domus"
					class="agent-avatar-fallback"
				/>
			</v-avatar>
		</div>

		<h2 class="welcome-title">
			您好，我是 <span class="agent-name">{{ store.currentAgentName || '数据助手' }}</span>
		</h2>
		<p class="welcome-desc">
			{{ store.currentAgentDescription || '告诉我你想了解的数据，我会完成查询、分析并整理成清晰的结果。' }}
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
	flex: 1;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: clamp(44px, 8vh, 92px) 24px 36px;
	text-align: center;
}

.welcome-eyebrow {
	margin: 0 0 20px;
	color: var(--domus-copper);
	font-size: 11px;
	font-weight: 800;
	text-transform: uppercase;
}

.agent-avatar-wrap {
	margin-bottom: 26px;
}

.agent-avatar {
	border: 1px solid var(--domus-line);
	background: rgb(255 246 232 / 82%) !important;
	box-shadow: 8px 10px 0 rgb(141 86 51 / 10%);
}

.agent-avatar-fallback {
	width: 66%;
	height: 66%;
	object-fit: contain;
}

.welcome-title {
	max-width: 760px;
	margin: 0 0 18px;
	color: var(--domus-ink);
	font-family: var(--domus-serif);
	font-size: clamp(36px, 5vw, 68px);
	font-weight: 700;
	line-height: 1.06;
}

.agent-name {
	color: var(--domus-copper);
}

.welcome-desc {
	max-width: 620px;
	margin: 0;
	color: var(--domus-muted);
	font-size: 15px;
	line-height: 1.8;
}

@media (max-width: 600px) {
	.welcome-wrap {
		justify-content: flex-start;
		padding: 46px 18px 24px;
	}

	.welcome-eyebrow {
		font-size: 10px;
	}

	.welcome-title {
		font-size: 38px;
	}
}
</style>
