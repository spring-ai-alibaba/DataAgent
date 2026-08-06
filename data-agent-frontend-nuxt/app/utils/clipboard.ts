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

/**
 * Copy text to the clipboard with a fallback for non-secure contexts.
 *
 * The async Clipboard API (`navigator.clipboard`) is only available in secure
 * contexts (HTTPS or localhost). When the app is served over plain HTTP on a
 * LAN IP, `navigator.clipboard` is `undefined`, so we fall back to the legacy
 * `document.execCommand('copy')` using a temporary textarea.
 *
 * @returns `true` if the copy succeeded, `false` otherwise.
 */
export async function copyTextToClipboard(text: string): Promise<boolean> {
	// Preferred path: async Clipboard API (secure contexts only).
	if (
		typeof navigator !== 'undefined' &&
		navigator.clipboard &&
		typeof navigator.clipboard.writeText === 'function' &&
		(typeof window === 'undefined' || window.isSecureContext !== false)
	) {
		try {
			await navigator.clipboard.writeText(text);
			return true;
		} catch {
			// Fall through to the legacy fallback below.
		}
	}

	return copyWithExecCommand(text);
}

/**
 * Legacy clipboard copy using a hidden textarea and `document.execCommand`.
 * Works in non-secure contexts where `navigator.clipboard` is unavailable.
 */
function copyWithExecCommand(text: string): boolean {
	if (typeof document === 'undefined') return false;

	const textarea = document.createElement('textarea');
	textarea.value = text;
	// Keep it out of view and prevent scrolling/zooming side effects.
	textarea.style.position = 'fixed';
	textarea.style.top = '-9999px';
	textarea.style.left = '-9999px';
	textarea.setAttribute('readonly', '');

	document.body.appendChild(textarea);
	try {
		textarea.select();
		textarea.setSelectionRange(0, text.length);
		return document.execCommand('copy');
	} catch {
		return false;
	} finally {
		document.body.removeChild(textarea);
	}
}
