export default defineNuxtConfig({
	compatibilityDate: '2025-07-15',
	devtools: { enabled: false },
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
		head: {
			title: 'Domus 智能问数',
			meta: [{ name: 'theme-color', content: '#F4EADC' }],
			link: [{ rel: 'icon', type: 'image/png', href: '/logo.png' }],
		},
		pageTransition: { name: 'page', mode: 'out-in' },
	},
	css: ['~/assets/css/main.css'],
});
