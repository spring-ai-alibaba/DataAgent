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

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
	compatibilityDate: '2025-07-15',
	devtools: { enabled: false },
	modules: ['vuetify-nuxt-module', '@pinia/nuxt', '@nuxt/eslint'],
	//基于组件名称自动导入
	components: [
		{
			path: '~/components', // 扫描 components 目录
			extensions: ['.vue'], // 确保只扫描 .vue 文件
			pathPrefix: false, // 禁用文件夹路径前缀
		},
	],
	imports: {
		dirs: [
			// 递归扫描所有的 index.ts，这样文件夹名就是函数名
			'composables/**/index.ts',
			'app/services/**/index.ts', // 匹配你规范中的 app/services/
			'composables/*.ts',
			'app/services/*.ts',
		],
	},
	vuetify: {
		vuetifyOptions: {
			theme: {
				defaultTheme: 'domus',
				themes: {
					domus: {
						dark: false,
						colors: {
							background: '#F4EADC',
							surface: '#FFF6E8',
							primary: '#8D5633',
							secondary: '#C7832F',
							info: '#77695C',
							success: '#4F704B',
							warning: '#A96B21',
							error: '#A44C3E',
						},
					},
				},
			},
			defaults: {
				VBtn: { variant: 'outlined', rounded: 'pill' },
				VCard: { rounded: 'lg' },
				VTextField: { color: 'primary' },
				VSelect: { color: 'primary' },
				VTextarea: { color: 'primary' },
			},
		},
	},
	vite: {
		build: {
			rollupOptions: {
				output: {
					manualChunks(id) {
						if (id.includes('/node_modules/zrender/')) return 'vendor-zrender';
						if (id.includes('/node_modules/echarts/')) return 'vendor-echarts';
						if (id.includes('/node_modules/highlight.js/'))
							return 'vendor-highlight';
					},
				},
			},
		},
	},
	//全局关闭ssr
	ssr: false,
	// /路由重定向到/create-agent
	routeRules: {
		'/': { redirect: '/agent/new' },
		// 代理所有 /api/** 的请求到 Java 后端
		'/api/**': { proxy: 'http://localhost:8065/api/**' },
		'/nl2sql/**': { proxy: 'http://localhost:8065/nl2sql/**' },
	},
	//全局动画配置
	app: {
		head: {
			title: 'Domus 智能问数控制台',
			meta: [{ name: 'theme-color', content: '#F4EADC' }],
			link: [{ rel: 'icon', type: 'image/png', href: '/logo.png' }],
		},
		pageTransition: { name: 'page', mode: 'out-in' },
	},
	css: ['@/assets/css/main.css'],
});
