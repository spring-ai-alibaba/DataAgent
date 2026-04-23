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

import axios from 'axios';
import { ApiResponse } from '@/services/common';
import { AgentDatasource } from '@/services/datasource';

interface ToggleDatasourceDto {
  datasourceId?: number;
  isActive?: boolean;
}

interface UpdateDatasourceTablesDto {
  datasourceId?: number;
  tables?: string[];
}

interface TableColumnsSelectionDto {
  tableName: string;
  columns?: string[];
}

interface UpdateDatasourceColumnsDto {
  datasourceId?: number;
  tables?: TableColumnsSelectionDto[];
}

const BASE_URL_FUNC = (agentId: string) => `/api/agent/${agentId}/datasources`;

const extractApiErrorMessage = (error: unknown, fallback: string): string => {
  if (axios.isAxiosError(error)) {
    const responseMessage = error.response?.data?.message;
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return responseMessage;
    }
    if (typeof error.message === 'string' && error.message.trim()) {
      return error.message;
    }
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return fallback;
};

class AgentDatasourceService {
  async initSchema(agentId: string): Promise<ApiResponse<null>> {
    try {
      const response = await axios.post<ApiResponse<null>>(`${BASE_URL_FUNC(agentId)}/init`);
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '初始化 Schema 失败'));
    }
  }

  async getAgentDatasource(agentId: number): Promise<AgentDatasource[]> {
    try {
      const response = await axios.get<ApiResponse<AgentDatasource[]>>(
        BASE_URL_FUNC(String(agentId)),
      );
      if (response.data.success) {
        return response.data.data || [];
      }
      throw new Error(response.data.message);
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '获取数据源列表失败'));
    }
  }

  async getActiveAgentDatasource(agentId: number): Promise<AgentDatasource> {
    try {
      const response = await axios.get<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(String(agentId))}/active`,
      );
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '后端返回了空数据');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '获取当前启用数据源失败'));
    }
  }

  async addDatasourceToAgent(
    agentId: string,
    datasourceId: number,
  ): Promise<ApiResponse<AgentDatasource>> {
    try {
      const response = await axios.post<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(agentId)}/${datasourceId}`,
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '添加数据源失败'));
    }
  }

  async removeDatasourceFromAgent(
    agentId: string,
    datasourceId: number,
  ): Promise<ApiResponse<null>> {
    try {
      const response = await axios.delete<ApiResponse<null>>(
        `${BASE_URL_FUNC(agentId)}/${datasourceId}`,
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '移除数据源失败'));
    }
  }

  async toggleDatasourceForAgent(
    agentId: string,
    dto: ToggleDatasourceDto,
  ): Promise<ApiResponse<AgentDatasource>> {
    try {
      const response = await axios.put<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(agentId)}/toggle`,
        dto,
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '切换数据源状态失败'));
    }
  }

  async updateDatasourceTables(
    agentId: string,
    dto: UpdateDatasourceTablesDto,
  ): Promise<ApiResponse<AgentDatasource>> {
    try {
      const response = await axios.post<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(agentId)}/tables`,
        dto,
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '更新数据表列表失败'));
    }
  }

  async updateDatasourceColumns(
    agentId: string,
    dto: UpdateDatasourceColumnsDto,
  ): Promise<ApiResponse<AgentDatasource>> {
    try {
      const response = await axios.post<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(agentId)}/columns`,
        dto,
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '更新字段可见性失败'));
    }
  }

  async getVisibleTableColumns(
    agentId: string,
    datasourceId: number,
    tableName: string,
  ): Promise<string[]> {
    try {
      const response = await axios.get<ApiResponse<string[]>>(
        `${BASE_URL_FUNC(agentId)}/${datasourceId}/tables/${encodeURIComponent(tableName)}/columns`,
      );
      if (response.data.success) {
        return response.data.data || [];
      }
      throw new Error(response.data.message);
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, `加载表 ${tableName} 的字段失败`));
    }
  }
}

export default new AgentDatasourceService();
