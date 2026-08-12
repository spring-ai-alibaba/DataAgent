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

import { describe, expect, it } from 'vitest';
import { resolveActiveDatasource } from './datasourceSelection';

describe('resolveActiveDatasource', () => {
	it('uses the datasource bound to the current agent instead of the first global datasource', () => {
		const result = resolveActiveDatasource(
			[
				{ id: 5, name: 'newest global datasource' },
				{ id: 3, name: 'agent datasource' },
			],
			3,
		);

		expect(result.activeDatasource?.name).toBe('agent datasource');
		expect(result.datasources).toEqual([
			{ id: 5, name: 'newest global datasource', isActive: false },
			{ id: 3, name: 'agent datasource', isActive: true },
		]);
	});

	it('does not mislabel a global datasource when the agent has no active binding', () => {
		const result = resolveActiveDatasource([{ id: 5, name: 'global datasource' }]);

		expect(result.activeDatasource).toBeNull();
		expect(result.datasources[0]?.isActive).toBe(false);
	});
});
