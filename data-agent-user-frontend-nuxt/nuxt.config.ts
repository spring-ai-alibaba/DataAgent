export default defineNuxtConfig({
	compatibilityDate: '2025-07-15',
	devtools: { enabled: true },
	modules: ['vuetify-nuxt-module', '@pinia/nuxt', '@nuxt/eslint'],
	components: [
		{
			path: '~/components',
			extensions: ['.vue'],
			pathPrefix: false,
		},
	],
	vuetify: {
		vuetifyOptions: {
			defaults: {
				VBtn: { variant: 'outlined' },
			},
		},
	},
	ssr: false,
	runtimeConfig: {
		public: {
			apiBaseUrl: '',
		},
	},
	routeRules: {
		'/': { redirect: '/chat' },
		'/api/**': { proxy: 'http://localhost:8065/api/**' },
		'/nl2sql/**': { proxy: 'http://localhost:8065/nl2sql/**' },
	},
	app: {
		pageTransition: { name: 'page', mode: 'out-in' },
	},
	css: ['~/assets/css/main.css'],
});
