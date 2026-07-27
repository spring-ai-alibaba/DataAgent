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

import axios from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import modelConfigService from './index';

vi.mock('axios', () => ({
	default: {
		post: vi.fn(),
	},
}));

describe('ModelConfigService.testConnection', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('submits only the saved config ID so masked credentials are never reused', async () => {
		vi.mocked(axios.post).mockResolvedValue({
			data: { success: true, message: 'ok' },
		});

		await modelConfigService.testConnection(42);

		expect(axios.post).toHaveBeenCalledWith('/api/model-config/test/42');
	});
});
