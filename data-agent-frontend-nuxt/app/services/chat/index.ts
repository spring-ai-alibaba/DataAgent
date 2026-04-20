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
 * @description 聊天会话服务，处理会话的创建、查询、删除、置顶以及消息的保存与报告下载
 */

import axios from 'axios';
import type { ApiResponse } from './common';

/**
 * @description 聊天会话实体接口
 */
export interface ChatSession {
  /** 会话 ID (UUID) */
  id: string;
  /** 关联的智能体 ID */
  agentId: number;
  /** 会话标题 */
  title: string;
  /** 状态 (active, archived, deleted) */
  status: string;
  /** 是否置顶 */
  isPinned: boolean;
  /** 用户 ID */
  userId?: number;
  /** 创建时间 */
  createTime?: Date;
  /** 更新时间 */
  updateTime?: Date;
}

/**
 * @description 聊天消息实体接口
 */
export interface ChatMessage {
  /** 消息 ID */
  id?: number;
  /** 所属会话 ID */
  sessionId: string;
  /** 角色 (user, assistant, system) */
  role: string;
  /** 消息内容 */
  content: string;
  /** 消息类型 (text, sql, result, error) */
  messageType: string;
  /** 元数据 (JSON 字符串) */
  metadata?: string;
  /** 创建时间 */
  createTime?: Date;
  /** 是否需要生成标题 */
  titleNeeded?: boolean;
}

const API_BASE_URL = '/api';

/**
 * @description 聊天业务逻辑处理类
 */
class ChatService {
  /**
   * @description 获取指定智能体的会话列表
   * @param {number} agentId - 智能体 ID
   * @returns {Promise<ChatSession[]>} 会话列表
   */
  async getAgentSessions(agentId: number): Promise<ChatSession[]> {
    const response = await axios.get<ChatSession[]>(`${API_BASE_URL}/agent/${agentId}/sessions`);
    return response.data;
  }

  /**
   * @description 创建新会话
   * @param {number} agentId - 智能体 ID
   * @param {string} [title] - 会话标题
   * @param {number} [userId] - 用户 ID
   * @returns {Promise<ChatSession>} 创建成功的会话详情
   */
  async createSession(agentId: number, title?: string, userId?: number): Promise<ChatSession> {
    const request = {
      title,
      userId,
    };

    const response = await axios.post<ChatSession>(
      `${API_BASE_URL}/agent/${agentId}/sessions`,
      request,
    );
    return response.data;
  }

  /**
   * @description 清空指定智能体的所有会话
   * @param {number} agentId - 智能体 ID
   * @returns {Promise<ApiResponse>} 操作结果
   */
  async clearAgentSessions(agentId: number): Promise<ApiResponse> {
    const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/agent/${agentId}/sessions`);
    return response.data;
  }

  /**
   * @description 获取指定会话的所有消息
   * @param {string} sessionId - 会话 ID
   * @returns {Promise<ChatMessage[]>} 消息列表
   */
  async getSessionMessages(sessionId: string): Promise<ChatMessage[]> {
    const response = await axios.get<ChatMessage[]>(
      `${API_BASE_URL}/sessions/${sessionId}/messages`,
    );
    return response.data;
  }

  /**
   * @description 保存消息到指定会话
   * @param {string} sessionId - 会话 ID
   * @param {ChatMessage} message - 消息内容
   * @returns {Promise<ChatMessage>} 保存后的消息详情
   */
  async saveMessage(sessionId: string, message: ChatMessage): Promise<ChatMessage> {
    try {
      const messageData = {
        ...message,
        sessionId,
      };

      const response = await axios.post<ChatMessage>(
        `${API_BASE_URL}/sessions/${sessionId}/messages`,
        messageData,
      );
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 500) {
        throw new Error('保存消息失败');
      }
      throw error;
    }
  }

  /**
   * @description 置顶或取消置顶会话
   * @param {string} sessionId - 会话 ID
   * @param {boolean} isPinned - 是否置顶
   * @returns {Promise<ApiResponse>} 操作结果
   */
  async pinSession(sessionId: string, isPinned: boolean): Promise<ApiResponse> {
    try {
      const response = await axios.put<ApiResponse>(
        `${API_BASE_URL}/sessions/${sessionId}/pin`,
        null,
        {
          params: { isPinned },
        },
      );
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 400) {
        throw new Error('isPinned参数不能为空');
      }
      if (axios.isAxiosError(error) && error.response?.status === 500) {
        throw new Error('操作失败');
      }
      throw error;
    }
  }

  /**
   * @description 重命名会话标题
   * @param {string} sessionId - 会话 ID
   * @param {string} title - 新标题
   * @returns {Promise<ApiResponse>} 操作结果
   */
  async renameSession(sessionId: string, title: string): Promise<ApiResponse> {
    try {
      if (!title || title.trim().length === 0) {
        throw new Error('标题不能为空');
      }

      const response = await axios.put<ApiResponse>(
        `${API_BASE_URL}/sessions/${sessionId}/rename`,
        null,
        {
          params: { title: title.trim() },
        },
      );
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 400) {
        throw new Error('标题不能为空');
      }
      if (axios.isAxiosError(error) && error.response?.status === 500) {
        throw new Error('重命名失败');
      }
      throw error;
    }
  }

  /**
   * @description 删除指定会话
   * @param {string} sessionId - 会话 ID
   * @returns {Promise<ApiResponse>} 操作结果
   */
  async deleteSession(sessionId: string): Promise<ApiResponse> {
    try {
      const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/sessions/${sessionId}`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 500) {
        throw new Error('删除失败');
      }
      throw error;
    }
  }

  /**
   * @description 下载会话的 HTML 报告
   * @param {string} sessionId - 会话 ID
   * @param {string} content - 报告内容
   * @returns {Promise<void>}
   */
  async downloadHtmlReport(sessionId: string, content: string): Promise<void> {
    try {
      const response = await axios.post(
        `${API_BASE_URL}/sessions/${sessionId}/reports/html`,
        content,
        {
          responseType: 'blob',
          headers: {
            'Content-Type': 'text/plain;charset=utf-8',
          },
        },
      );

      const contentDisposition = response.headers['content-disposition'];
      let filename = 'report.html';
      if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="?([^;"]+)"?/);
        if (filenameMatch && filenameMatch[1]) {
          filename = filenameMatch[1];
        }
      }

      const blob = new Blob([response.data], { type: 'text/html' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        throw new Error(`下载失败: ${error.message}`);
      }
      throw error;
    }
  }
}

export default new ChatService();
