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
	<v-dialog :model-value="modelValue" max-width="800" persistent @update:model-value="$emit('update:modelValue', $event)">
		<v-card rounded="xl" class="pa-2">
			<v-card-title class="d-flex align-center justify-space-between px-4 pt-4">
				<span class="text-h6 font-weight-bold">{{ isEdit ? '编辑数据源' : '添加数据源' }}</span>
				<v-btn icon="mdi-close" variant="text" size="small" @click="$emit('update:modelValue', false)" />
			</v-card-title>

			<v-card-text class="pt-4">
				<v-form ref="formRef" v-model="formValid">
					<v-row dense>
						<v-col cols="12" md="6">
							<span class="custom-label">数据源名称 *</span>
							<v-text-field v-model="form.name" placeholder="请输入名称" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col cols="12" md="6">
							<span class="custom-label">数据库类型 *</span>
							<v-select v-model="form.type" :items="['mysql', 'postgresql', 'sqlserver', 'dameng', 'oracle']" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>

						<v-col cols="12" md="8">
							<span class="custom-label">主机地址 *</span>
							<v-text-field v-model="form.host" placeholder="localhost 或 IP 地址" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col cols="12" md="4">
							<span class="custom-label">端口号 *</span>
							<v-text-field v-model.number="form.port" type="number" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>

						<v-col cols="12" :md="['postgresql', 'oracle'].includes(form.type || '') ? 6 : 12">
							<span class="custom-label">数据库名 *</span>
							<v-text-field v-model="form.databaseName" placeholder="Database Name" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col v-if="['postgresql', 'oracle'].includes(form.type || '')" cols="12" md="6">
							<span class="custom-label">Schema 名</span>
							<v-text-field v-model="form.schemaName" placeholder="如 public" variant="outlined" density="compact" />
						</v-col>

						<v-col cols="12">
							<span class="custom-label">JDBC 连接地址 (可选)</span>
							<v-text-field v-model="form.connectionUrl" placeholder="若不填则自动生成" variant="outlined" density="compact" />
						</v-col>

						<v-col cols="12" md="6">
							<span class="custom-label">用户名 *</span>
							<v-text-field v-model="form.username" placeholder="Username" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col cols="12" md="6">
							<span class="custom-label">密码 *</span>
							<v-text-field v-model="form.password" type="password" placeholder="Password" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>

						<v-col cols="12">
							<span class="custom-label">描述信息</span>
							<v-textarea v-model="form.description" rows="2" placeholder="可选描述" variant="outlined" density="compact" />
						</v-col>
					</v-row>
				</v-form>
				<div class="d-flex justify-end mt-4 ga-3">
					<v-btn variant="text" class="text-none" @click="$emit('update:modelValue', false)">取消</v-btn>
					<v-btn color="primary" class="text-none px-8" elevation="0" :loading="saving" @click="handleSubmit">
						{{ isEdit ? '保存' : '创建' }}
					</v-btn>
				</div>
			</v-card-text>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import type { VForm } from 'vuetify/components';
import type { Datasource } from '@/services/datasource';

const props = defineProps<{
	modelValue: boolean;
	isEdit: boolean;
	datasource: Datasource | null;
	saving: boolean;
}>();

const emit = defineEmits<{
	'update:modelValue': [value: boolean];
	submit: [data: Datasource];
}>();

const formRef = ref<VForm | null>(null);
const formValid = ref(false);

const defaultForm = (): Datasource => ({
	name: '', type: 'mysql', host: '', port: 3306,
	databaseName: '', schemaName: '', username: '', password: '',
	description: '', connectionUrl: '',
});

const form = reactive<Datasource>(defaultForm());

const rules = { required: (v: unknown) => !!v || '此项必填' };

watch(() => props.modelValue, (visible) => {
	if (!visible) return;
	if (props.isEdit && props.datasource) {
		Object.assign(form, props.datasource);
	} else {
		Object.assign(form, defaultForm());
	}
});

async function handleSubmit() {
	const { valid } = (await formRef.value?.validate()) || { valid: false };
	if (!valid) return;
	emit('submit', { ...form });
}
</script>
