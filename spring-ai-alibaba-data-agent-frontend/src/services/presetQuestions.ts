/*
 * Copyright 2025 the original author or authors.
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

interface PresetQuestion {
    id?: number;
    content: string;
    order?: number;
    agentId?: number;
    createTime?: Date | string;
    updateTime?: Date | string;
}

// 后端GET返回的数据结构
interface ApiPresetQuestionResponseItem {
    id: number;
    agentId: number;
    question: string;
    sortOrder: number;
    isActive: boolean;
    createTime: string;
    updateTime: string;
}

// 后端POST请求体为 List<Map<String, String>>，此处用宽松类型
type ApiSaveRequestItem = Record<string, any>;

const API_BASE_URL = '/api/agent';

class PresetQuestionsService {
    /**
     * 获取智能体的预设问题列表
     * @param agentId 智能体ID
     */
    async getQuestions(agentId: number): Promise<PresetQuestion[]> {
        try {
            const response = await axios.get<ApiPresetQuestionResponseItem[]>(`${API_BASE_URL}/${agentId}/preset-questions`);
            const list = response.data || [];
            // 映射为前端结构
            return list.map(item => ({
                id: item.id,
                agentId: item.agentId,
                content: item.question,
                order: item.sortOrder,
                createTime: item.createTime,
                updateTime: item.updateTime
            }));
        } catch (error) {
            if (axios.isAxiosError(error) && error.response?.status === 404) {
                return [];
            }
            throw error;
        }
    }

    /**
     * 保存智能体的预设问题
     * @param agentId 智能体ID
     * @param questions 问题列表
     */
    async saveQuestions(agentId: number, questions: PresetQuestion[]): Promise<{ message?: string }> {
        // 后端要求：请求体为 List<Map<String, String>>
        const payload: ApiSaveRequestItem[] = questions.map((q, index) => ({
            question: String(q.content ?? ''),
            sortOrder: Number(q.order ?? (index + 1))
        }));

        const response = await axios.post<{ message?: string }>(
            `${API_BASE_URL}/${agentId}/preset-questions`,
            payload
        );
        return response.data;
    }

    /**
     * 删除单个预设问题
     * @param questionId 问题ID
     */
    async deleteQuestion(questionId: number): Promise<boolean> {
        try {
            await axios.delete(`${API_BASE_URL}/preset-questions/${questionId}`);
            return true;
        } catch (error) {
            if (axios.isAxiosError(error) && error.response?.status === 404) {
                return false;
            }
            throw error;
        }
    }

    /**
     * 批量删除智能体的所有预设问题
     * @param agentId 智能体ID
     */
    async deleteAllQuestions(agentId: number): Promise<boolean> {
        try {
            await axios.delete(`${API_BASE_URL}/${agentId}/preset-questions`);
            return true;
        } catch (error) {
            if (axios.isAxiosError(error) && error.response?.status === 404) {
                return false;
            }
            throw error;
        }
    }
}

export const presetQuestionsApi = new PresetQuestionsService();
export default presetQuestionsApi;
