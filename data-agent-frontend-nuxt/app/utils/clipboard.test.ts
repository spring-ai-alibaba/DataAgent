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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { copyTextToClipboard } from './clipboard';

/**
 * Minimal fake DOM so the execCommand fallback can be exercised in the default
 * node test environment (no jsdom dependency, matching the rest of the suite).
 */
function stubFakeDocument(execResult: boolean) {
	const appended: unknown[] = [];
	const execCommand = vi.fn().mockReturnValue(execResult);
	const fakeDocument = {
		createElement: vi.fn(() => ({
			style: {} as Record<string, string>,
			setAttribute: vi.fn(),
			select: vi.fn(),
			setSelectionRange: vi.fn(),
			value: '',
		})),
		body: {
			appendChild: vi.fn((el: unknown) => appended.push(el)),
			removeChild: vi.fn((el: unknown) => {
				const i = appended.indexOf(el);
				if (i >= 0) appended.splice(i, 1);
			}),
		},
		execCommand,
	};
	vi.stubGlobal('document', fakeDocument);
	return { appended, execCommand };
}

describe('copyTextToClipboard', () => {
	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
	});

	it('uses navigator.clipboard in a secure context', async () => {
		const writeText = vi.fn().mockResolvedValue(undefined);
		vi.stubGlobal('window', { isSecureContext: true });
		vi.stubGlobal('navigator', { clipboard: { writeText } });

		const result = await copyTextToClipboard('SELECT 1');

		expect(result).toBe(true);
		expect(writeText).toHaveBeenCalledWith('SELECT 1');
	});

	it('falls back to execCommand when writeText rejects', async () => {
		const writeText = vi.fn().mockRejectedValue(new Error('denied'));
		vi.stubGlobal('window', { isSecureContext: true });
		vi.stubGlobal('navigator', { clipboard: { writeText } });
		const { execCommand } = stubFakeDocument(true);

		const result = await copyTextToClipboard('SELECT 1');

		expect(result).toBe(true);
		expect(execCommand).toHaveBeenCalledWith('copy');
	});

	it('falls back to execCommand over plain HTTP on a LAN IP', async () => {
		// navigator.clipboard is undefined in a non-secure context.
		vi.stubGlobal('window', { isSecureContext: false });
		vi.stubGlobal('navigator', {});
		const { execCommand } = stubFakeDocument(true);

		const result = await copyTextToClipboard('hello world');

		expect(result).toBe(true);
		expect(execCommand).toHaveBeenCalledWith('copy');
	});

	it('returns false when execCommand reports failure', async () => {
		vi.stubGlobal('window', { isSecureContext: false });
		vi.stubGlobal('navigator', {});
		stubFakeDocument(false);

		const result = await copyTextToClipboard('hello world');

		expect(result).toBe(false);
	});

	it('removes the temporary textarea after copying', async () => {
		vi.stubGlobal('window', { isSecureContext: false });
		vi.stubGlobal('navigator', {});
		const { appended } = stubFakeDocument(true);

		await copyTextToClipboard('cleanup check');

		expect(appended).toHaveLength(0);
	});
});
