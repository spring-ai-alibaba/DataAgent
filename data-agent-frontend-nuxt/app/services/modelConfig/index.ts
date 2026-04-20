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
 * @description 模型配置管理服务，处理 LLM 供应商信息、API 密钥、模型参数及就绪状态检查
 */

import axios from "axios";
import type { ApiResponse } from "./common";

/**
 * @description 模型类型枚举
 */
export type ModelType = "CHAT" | "EMBEDDING";

/**
 * @description 模型配置实体接口
 */
export interface ModelConfig {
  /** 配置 ID */
  id?: number;
  /** 供应商 (如 openai, deepseek) */
  provider: string;
  /** API 密钥 */
  apiKey: string;
  /** 基础 URL */
  baseUrl: string;
  /** 模型名称 */
  modelName: string;
  /** 模型类型 */
  modelType: ModelType;
  /** 温度参数 (0-2) */
  temperature?: number;
  /** 最大生成 Token 数 */
  maxTokens?: number;
  /** 是否激活 */
  isActive?: boolean;
  /** 对话模型路径 */
  completionsPath?: string;
  /** 嵌入模型路径 */
  embeddingsPath?: string;
}

/**
 * @description 模型就绪状态检查结果接口
 */
export interface ModelCheckReady {
  /** 对话模型是否已配置 */
  chatModelReady: boolean;
  /** 嵌入模型是否已配置 */
  embeddingModelReady: boolean;
  /** 整体是否就绪 */
  ready: boolean;
}

const API_BASE_URL = "/api/model-config";

/**
 * @description 模型配置业务逻辑处理类
 */
class ModelConfigService {
  /**
   * @description 获取所有模型配置列表
   * @returns {Promise<ModelConfig[]>} 配置列表
   */
  async list(): Promise<ModelConfig[]> {
    const response = await axios.get<ApiResponse<ModelConfig[]>>(
      `${API_BASE_URL}/list`,
    );
    return response.data.data || [];
  }

  /**
   * @description 新增模型配置
   * @param {Omit<ModelConfig, "id">} config - 配置信息
   * @returns {Promise<ApiResponse<string>>} 操作结果
   */
  async add(config: Omit<ModelConfig, "id">): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(
      `${API_BASE_URL}/add`,
      config,
    );
    return response.data;
  }

  /**
   * @description 更新模型配置信息
   * @param {ModelConfig} config - 配置信息
   * @returns {Promise<ApiResponse<string>>} 操作结果
   */
  async update(config: ModelConfig): Promise<ApiResponse<string>> {
    const response = await axios.put<ApiResponse<string>>(
      `${API_BASE_URL}/update`,
      config,
    );
    return response.data;
  }

  /**
   * @description 删除指定模型配置
   * @param {number} id - 配置 ID
   * @returns {Promise<ApiResponse<string>>} 操作结果
   */
  async delete(id: number): Promise<ApiResponse<string>> {
    const response = await axios.delete<ApiResponse<string>>(
      `${API_BASE_URL}/${id}`,
    );
    return response.data;
  }

  /**
   * @description 启用或切换当前激活的模型配置
   * @param {number} id - 配置 ID
   * @returns {Promise<ApiResponse<string>>} 操作结果
   */
  async activate(id: number): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(
      `${API_BASE_URL}/activate/${id}`,
    );
    return response.data;
  }

  /**
   * @description 测试模型配置的连接有效性
   * @param {Omit<ModelConfig, "id">} config - 配置信息
   * @returns {Promise<ApiResponse<string>>} 测试结果消息
   */
  async testConnection(
    config: Omit<ModelConfig, "id">,
  ): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(
      `${API_BASE_URL}/test`,
      config,
    );
    return response.data;
  }

  /**
   * @description 检查模型配置是否整体就绪 (对话和嵌入模型均需配置)
   * @returns {Promise<ModelCheckReady>} 就绪状态
   */
  async checkReady(): Promise<ModelCheckReady> {
    const response = await axios.get<ApiResponse<ModelCheckReady>>(
      `${API_BASE_URL}/check-ready`,
    );
    return (
      response.data.data || {
        chatModelReady: false,
        embeddingModelReady: false,
        ready: false,
      }
    );
  }
}

export default new ModelConfigService();
