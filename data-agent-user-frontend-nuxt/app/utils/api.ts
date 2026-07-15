let configuredApiBaseUrl = '';

function normalizeBaseUrl(value: string) {
	return value.trim().replace(/\/$/, '');
}

export function configureApiBaseUrl(value?: string) {
	configuredApiBaseUrl = normalizeBaseUrl(value || '');
}

export function buildApiUrl(path: string) {
	const normalizedPath = path.startsWith('/') ? path : `/${path}`;
	return `${configuredApiBaseUrl}${normalizedPath}`;
}
