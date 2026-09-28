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
import chatService from './index';

vi.mock('axios', () => ({
	default: {
		delete: vi.fn(),
		isAxiosError: vi.fn(),
	},
}));

describe('ChatService.deleteSession', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('sends the owning agent ID with the session deletion request', async () => {
		vi.mocked(axios.delete).mockResolvedValue({
			data: { success: true, message: 'deleted' },
		});

		await chatService.deleteSession('conversation-1', 7);

		expect(axios.delete).toHaveBeenCalledWith(
			'/api/sessions/conversation-1',
			{
				params: { agentId: 7 },
			},
		);
	});
});
