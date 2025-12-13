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
import type { ApiResponse } from './common';

export interface ChatSession {
  id: string; // UUID
  agentId: number;
  title: string;
  status: string; // active, archived, deleted
  isPinned: boolean; // Whether pinned
  userId?: number;
  createTime?: Date;
  updateTime?: Date;
}

export interface ChatMessage {
  id?: number;
  sessionId: string;
  role: string; // user, assistant, system
  content: string;
  messageType: string; // text, sql, result, error
  metadata?: string; // JSON格式的元数据
  createTime?: Date;
  titleNeeded?: boolean;
}

const API_BASE_URL = '/api';

class ChatService {
  /**
   * 获取Agent的会话列表
   * @param agentId Agent ID
   */
  async getAgentSessions(agentId: number): Promise<ChatSession[]> {
    const response = await axios.get<ChatSession[]>(`${API_BASE_URL}/agent/${agentId}/sessions`);
    return response.data;
  }

  /**
   * 创建新会话
   * @param agentId Agent ID
   * @param title 会话标题
   * @param userId 用户ID
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
   * 清空Agent的所有会话
   * @param agentId Agent ID
   */
  async clearAgentSessions(agentId: number): Promise<ApiResponse> {
    const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/agent/${agentId}/sessions`);
    return response.data;
  }

  /**
   * 获取会话的消息列表
   * @param sessionId 会话ID
   */
  async getSessionMessages(sessionId: string): Promise<ChatMessage[]> {
    const response = await axios.get<ChatMessage[]>(
      `${API_BASE_URL}/sessions/${sessionId}/messages`,
    );
    return response.data;
  }

  /**
   * 保存消息到会话
   * @param sessionId 会话ID
   * @param message 消息对象
   */
  async saveMessage(sessionId: string, message: ChatMessage): Promise<ChatMessage> {
    try {
      // 设置会话ID
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
   * 置顶/取消置顶会话
   * @param sessionId 会话ID
   * @param isPinned 是否置顶
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
   * 重命名会话
   * @param sessionId 会话ID
   * @param title 新标题
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
   * 删除单个会话
   * @param sessionId 会话ID
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
}

export default new ChatService();
