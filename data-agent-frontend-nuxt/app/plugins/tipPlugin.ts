import { useTipStore } from '~/stores/tips';

export default defineNuxtPlugin(() => {
	const tipStore = useTipStore();

	if (import.meta.client) {
		window.__tipShow = tipStore.show;
	}

	return {
		provide: {
			tip: tipStore.show,
		},
	};
});
