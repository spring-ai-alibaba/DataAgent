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

/**
 * @description 智能体管理服务，处理智能体的增删改查、发布、下线及 API Key 管理
 */

import axios from 'axios';
import type { ApiResponse } from '../common';

/**
 * @description 智能体实体定义
 */
export interface Agent {
	/** 智能体 ID */
	id?: number;
	/** 智能体名称 */
	name?: string;
	/** 智能体描述 */
	description?: string;
	/** 智能体头像 URL */
	avatar?: string;
	/** 状态 (draft, published, offline) */
	status?: string;
	/** API Key */
	apiKey?: string | null;
	/** 是否启用 API Key */
	apiKeyEnabled?: number | boolean;
	/** 提示词 (Prompt) */
	prompt?: string;
	/** 分类 */
	category?: string;
	/** 管理员 ID */
	adminId?: number;
	/** 标签 */
	tags?: string;
	/** 创建时间 */
	createTime?: Date;
	/** 更新时间 */
	updateTime?: Date;
	/** 是否启用人工审核 (0 或 1) */
	humanReviewEnabled?: number | boolean;
}

const API_BASE_URL = '/api/agent';

/**
 * @description 智能体 API Key 响应结构
 */
export interface AgentApiKeyResponse {
	/** API Key 内容 */
	apiKey: string | null;
	/** 是否启用 */
	apiKeyEnabled: number | boolean;
}

/**
 * @description 智能体 API Key 接口返回结果
 */
export type AgentApiKeyApiResult = ApiResponse<AgentApiKeyResponse>;

/**
 * @description 智能体业务逻辑处理类
 */
class AgentService {
	/**
	 * @description 获取智能体列表
	 * @param {string} [status] - 状态筛选
	 * @param {string} [keyword] - 关键词搜索
	 * @returns {Promise<Agent[]>} 智能体列表
	 */
	async list(status?: string, keyword?: string): Promise<Agent[]> {
		const params: Record<string, string> = {};
		if (status) params.status = status;
		if (keyword) params.keyword = keyword;

		const response = await axios.get<Agent[]>(`${API_BASE_URL}/list`, {
			params,
		});
		return response.data;
	}

	/**
	 * @description 根据 ID 获取智能体详情
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<Agent | null>} 智能体详情，不存在返回 null
	 */
	async get(id: number): Promise<Agent | null> {
		try {
			const response = await axios.get<Agent>(`${API_BASE_URL}/${id}`);
			return response.data;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	/**
	 * @description 创建新智能体
	 * @param {Omit<Agent, 'id'>} agent - 智能体信息
	 * @returns {Promise<Agent>} 创建成功的智能体
	 */
	async create(agent: Omit<Agent, 'id'>): Promise<Agent> {
		const agentData = {
			...agent,
			status: agent.status || 'draft',
		};

		const response = await axios.post<Agent>(API_BASE_URL, agentData);
		return response.data;
	}

	/**
	 * @description 更新智能体信息
	 * @param {number} id - 智能体 ID
	 * @param {Partial<Agent>} agent - 需要更新的字段
	 * @returns {Promise<Agent | null>} 更新后的智能体详情
	 */
	async update(id: number, agent: Partial<Agent>): Promise<Agent | null> {
		try {
			const agentData = {
				id: agent.id,
				name: agent.name,
				description: agent.description,
				avatar: agent.avatar,
				status: agent.status,
				prompt: agent.prompt,
				category: agent.category,
				tags: agent.tags,
				humanReviewEnabled: agent.humanReviewEnabled ? 1 : 0,
			};
			const response = await axios.put<Agent>(
				`${API_BASE_URL}/${id}`,
				agentData,
			);
			return response.data;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	/**
	 * @description 删除指定智能体
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<boolean>} 是否删除成功
	 */
	async delete(id: number): Promise<boolean> {
		try {
			await axios.delete(`${API_BASE_URL}/${id}`);
			return true;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return false;
			}
			throw error;
		}
	}

	/**
	 * @description 发布智能体
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<Agent | null>} 发布后的智能体详情
	 */
	async publish(id: number): Promise<Agent | null> {
		try {
			const response = await axios.post<Agent>(`${API_BASE_URL}/${id}/publish`);
			return response.data;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	/**
	 * @description 下线智能体
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<Agent | null>} 下线后的智能体详情
	 */
	async offline(id: number): Promise<Agent | null> {
		try {
			const response = await axios.post<Agent>(`${API_BASE_URL}/${id}/offline`);
			return response.data;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	/**
	 * @description 获取智能体的 API Key (遮罩态)
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<AgentApiKeyResponse | null>} API Key 信息
	 */
	async getApiKey(id: number): Promise<AgentApiKeyResponse | null> {
		try {
			const response = await axios.get<AgentApiKeyApiResult>(
				`${API_BASE_URL}/${id}/api-key`,
			);
			if (response.data.success) {
				return response.data.data ?? null;
			}
			throw new Error(response.data.message);
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	/**
	 * @description 为智能体生成 API Key
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<AgentApiKeyResponse>} 生成的 API Key 信息
	 */
	async generateApiKey(id: number): Promise<AgentApiKeyResponse> {
		const response = await axios.post<AgentApiKeyApiResult>(
			`${API_BASE_URL}/${id}/api-key/generate`,
		);
		if (response.data.success && response.data.data) {
			return response.data.data;
		}
		throw new Error(response.data.message || '生成 API Key 失败');
	}

	/**
	 * @description 重置智能体的 API Key
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<AgentApiKeyResponse>} 重置后的 API Key 信息
	 */
	async resetApiKey(id: number): Promise<AgentApiKeyResponse> {
		const response = await axios.post<AgentApiKeyApiResult>(
			`${API_BASE_URL}/${id}/api-key/reset`,
		);
		if (response.data.success && response.data.data) {
			return response.data.data;
		}
		throw new Error(response.data.message || '重置 API Key 失败');
	}

	/**
	 * @description 删除智能体的 API Key
	 * @param {number} id - 智能体 ID
	 * @returns {Promise<AgentApiKeyResponse>} 操作结果
	 */
	async deleteApiKey(id: number): Promise<AgentApiKeyResponse> {
		const response = await axios.delete<AgentApiKeyApiResult>(
			`${API_BASE_URL}/${id}/api-key`,
		);
		if (response.data.success && response.data.data) {
			return response.data.data;
		}
		throw new Error(response.data.message || '删除 API Key 失败');
	}

	/**
	 * @description 启用或禁用智能体的 API Key
	 * @param {number} id - 智能体 ID
	 * @param {boolean} enabled - 是否启用
	 * @returns {Promise<AgentApiKeyResponse>} 更新后的 API Key 状态
	 */
	async toggleApiKey(
		id: number,
		enabled: boolean,
	): Promise<AgentApiKeyResponse> {
		const response = await axios.post<AgentApiKeyApiResult>(
			`${API_BASE_URL}/${id}/api-key/enable`,
			null,
			{
				params: { enabled },
			},
		);
		if (response.data.success && response.data.data) {
			return response.data.data;
		}
		throw new Error(response.data.message || '更新 API Key 状态失败');
	}
}

export default new AgentService();
