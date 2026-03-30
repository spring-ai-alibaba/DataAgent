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
