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

// @ts-check
import withNuxt from './.nuxt/eslint.config.mjs';

export default withNuxt({
	rules: {
		//关闭强制要求自闭和标签
		'vue/html-self-closing': 'off',
		//关闭强制多个单词
		'vue/multi-word-component-names': 'off',
		// 允许 v-slot:item.xxx 语法（Vuetify v-data-table 插槽）
		'vue/no-v-html': 'off',
		'vue/valid-v-slot': ['error', { allowModifiers: true }],
	},
});
