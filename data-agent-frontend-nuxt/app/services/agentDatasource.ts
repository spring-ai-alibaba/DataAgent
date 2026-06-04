/*
 * Copyright 2024-2025 the original author or authors.
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

import { $fetch } from 'ofetch';

export interface ApiResponse<T> {
	success: boolean;
	message?: string;
	data?: T;
}

export interface AgentDatasource {
	id?: number;
	agentId?: number;
	datasourceId?: number;
	isActive?: number;
	selectTables?: string[];
	datasource?: any;
}

const BASE_URL_FUNC = (agentId: string) => `/api/agent/${agentId}/datasources`;

class AgentDatasourceService {
	/**
	 * 初始化数据源Schema
	 * @param agentId 智能体ID
	 */
	async initSchema(agentId: string): Promise<ApiResponse<null>> {
		return await $fetch<ApiResponse<null>>(`${BASE_URL_FUNC(agentId)}/init`, {
			method: 'POST',
		});
	}

	/**
	 * 获取当前激活的智能体数据源
	 * @param agentId 智能体ID
	 */
	async getActiveAgentDatasource(agentId: string): Promise<ApiResponse<AgentDatasource>> {
		return await $fetch<ApiResponse<AgentDatasource>>(
			`${BASE_URL_FUNC(agentId)}/active`,
		);
	}
}

export default new AgentDatasourceService();
