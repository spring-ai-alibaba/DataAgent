import axios from 'axios';
import { configureApiBaseUrl } from '~/utils/api';

export default defineNuxtPlugin(() => {
	const config = useRuntimeConfig();
	const apiBaseUrl = String(config.public.apiBaseUrl || '')
		.trim()
		.replace(/\/$/, '');

	configureApiBaseUrl(apiBaseUrl);
	axios.defaults.baseURL = apiBaseUrl || undefined;
});
