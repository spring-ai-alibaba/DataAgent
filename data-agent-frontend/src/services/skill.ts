/*
 * Copyright 2024-2026 the original author or authors.
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

export interface LocalSkillSummary {
  id: string;
  title: string;
  description: string;
  builtin: boolean;
  resourceCount: number;
  updateTime?: string;
}

export interface LocalSkillDetail {
  id: string;
  title: string;
  description: string;
  builtin: boolean;
  content: string;
  resources: string[];
}

export interface AgentSkillConfig {
  storagePath: string;
  selectedSkillIds: string[];
  skills: LocalSkillSummary[];
}

export interface SaveLocalSkillPayload {
  id?: string;
  title: string;
  description: string;
  content: string;
}

class SkillService {
  async list(): Promise<LocalSkillSummary[]> {
    const response = await axios.get<LocalSkillSummary[]>('/api/skills');
    return response.data;
  }

  async get(skillId: string): Promise<LocalSkillDetail> {
    const response = await axios.get<LocalSkillDetail>(`/api/skills/${skillId}`);
    return response.data;
  }

  async create(payload: SaveLocalSkillPayload): Promise<LocalSkillDetail> {
    const response = await axios.post<LocalSkillDetail>('/api/skills', payload);
    return response.data;
  }

  async update(skillId: string, payload: SaveLocalSkillPayload): Promise<LocalSkillDetail> {
    const response = await axios.put<LocalSkillDetail>(`/api/skills/${skillId}`, payload);
    return response.data;
  }

  async delete(skillId: string): Promise<void> {
    await axios.delete(`/api/skills/${skillId}`);
  }

  async getAgentSkillConfig(agentId: number): Promise<AgentSkillConfig> {
    const response = await axios.get<AgentSkillConfig>(`/api/agent/${agentId}/skills`);
    return response.data;
  }

  async updateAgentSkills(agentId: number, skillIds: string[]): Promise<AgentSkillConfig> {
    const response = await axios.put<AgentSkillConfig>(`/api/agent/${agentId}/skills`, {
      skillIds,
    });
    return response.data;
  }
}

export default new SkillService();
