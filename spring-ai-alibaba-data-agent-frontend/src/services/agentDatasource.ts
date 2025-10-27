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

import axios from "axios";
import { ApiResponse } from '@/services/common'
import { AgentDatasource } from '@/services/datasource'

interface InitSchemaDto {
    datasourceId?: number;
    tables?: string[];
}

interface ToggleDatasourceDto {
    datasourceId?: number;
    isActive?: boolean;
}

const BASE_URL_FUNC = (agentId: string) => `/api/agent/${agentId}/datasources`;

class AgentDatasourceService {
    /**
     * 初始化数据源Schema
     * @param agentId 智能体ID
     * @param dto 初始化参数
     */
    async initSchema(agentId: string, dto: InitSchemaDto): Promise<ApiResponse<null>> {
        try {
            const response = await axios.post<ApiResponse<null>>(`${BASE_URL_FUNC(agentId)}/init`, dto);
            return response.data;
        } catch (error) {
            throw new Error(`初始化Schema失败: ${error}`);
        }
    }

    /**
     * 获取智能体的数据源列表
     * @param agentId 智能体ID
     */
    async getAgentDatasource(agentId: number): Promise<AgentDatasource[]> {
        try {
            const response: ApiResponse<AgentDatasource[]> = (await axios.get<ApiResponse<AgentDatasource[]>>(BASE_URL_FUNC(String(agentId)))).data;
            if(response.success) {
                return response.data;
            }
            throw new Error(response.message);
        } catch (error) {
            throw new Error(`获取数据源列表失败: ${error}`);
        }
    }

    /**
     * 获取数据源的表列表
     * @param agentId 智能体ID
     * @param datasourceId 数据源ID
     */
    async getDatasourceTables(agentId: string, datasourceId: number): Promise<ApiResponse<string[]>> {
        try {
            const response = await axios.get<ApiResponse<string[]>>(`${BASE_URL_FUNC(agentId)}/${datasourceId}/tables`);
            return response.data;
        } catch (error) {
            throw new Error(`获取表列表失败: ${error}`);
        }
    }

    /**
     * 为智能体添加数据源
     * @param agentId 智能体ID
     * @param datasourceId 数据源ID
     */
    async addDatasourceToAgent(agentId: string, datasourceId: number): Promise<ApiResponse<AgentDatasource>> {
        try {
            const response = await axios.post<ApiResponse<AgentDatasource>>(`${BASE_URL_FUNC(agentId)}/${datasourceId}`);
            return response.data;
        } catch (error) {
            throw new Error(`添加数据源失败: ${error}`);
        }
    }

    /**
     * 从智能体移除数据源
     * @param agentId 智能体ID
     * @param datasourceId 数据源ID
     */
    async removeDatasourceFromAgent(agentId: string, datasourceId: number): Promise<ApiResponse<null>> {
        try {
            const response = await axios.delete<ApiResponse<null>>(`${BASE_URL_FUNC(agentId)}/${datasourceId}`);
            return response.data;
        } catch (error) {
            throw new Error(`移除数据源失败: ${error}`);
        }
    }

    /**
     * 启用/禁用智能体的数据源
     * @param agentId 智能体ID
     * @param dto 切换参数
     */
    async toggleDatasourceForAgent(agentId: string, dto: ToggleDatasourceDto): Promise<ApiResponse<AgentDatasource>> {
        try {
            const response = await axios.put<ApiResponse<AgentDatasource>>(`${BASE_URL_FUNC(agentId)}/toggle`, dto);
            return response.data;
        } catch (error) {
            throw new Error(`切换数据源状态失败: ${error}`);
        }
    }
}

export default new AgentDatasourceService()
