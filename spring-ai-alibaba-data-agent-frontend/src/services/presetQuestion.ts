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

interface PresetQuestion {
  id?: number;
  agentId: number;
  question: string;
  sortOrder?: number;
  isActive?: boolean;
  createTime?: string;
  updateTime?: string;
}

interface PresetQuestionDTO {
  question: string;
  isActive?: boolean;
}

const API_BASE_URL = '/api/agent';

class PresetQuestionService {
  /**
   * 获取预设问题列表
   * @param agentId
   * @returns
   */
  async list(agentId: number): Promise<PresetQuestion[]> {
    try {
      const response = await axios.get<PresetQuestion[]>(
        `${API_BASE_URL}/${agentId}/preset-questions`,
      );
      return response.data;
    } catch (error) {
      console.error('获取预设问题列表失败:', error);
      throw error;
    }
  }

  /**
   * 批量保存预设问题
   * @param agentId Agent ID
   * @param questions 问题列表
   */
  async batchSave(agentId: number, questions: PresetQuestionDTO[]): Promise<boolean> {
    try {
      const questionsData = questions.map(q => ({
        question: q.question,
        isActive: q.isActive ?? true,
      }));
      const response = await axios.post(
        `${API_BASE_URL}/${agentId}/preset-questions`,
        questionsData,
      );
      return response.status === 200 || response.status === 201;
    } catch (error) {
      console.error('保存预设问题失败:', error);
      throw error;
    }
  }

  /**
   * 删除预设问题
   * @param agentId Agent ID
   * @param questionId 问题 ID
   */
  async delete(agentId: number, questionId: number): Promise<boolean> {
    try {
      await axios.delete(`${API_BASE_URL}/${agentId}/preset-questions/${questionId}`);
      return true;
    } catch (error) {
      console.error('删除预设问题失败:', error);
      throw error;
    }
  }
}

export type { PresetQuestion, PresetQuestionDTO };
export default new PresetQuestionService();
