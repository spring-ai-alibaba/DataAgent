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

const BASE_URL_FUNC = (agentId: string) => `/api/agent/${agentId}/schema`;

class AgentSchemaService {
    async getDatasourceTables(agentId: string, datasourceId: string): Promise<ApiResponse<string[]>> {
        const response = await axios.get(BASE_URL_FUNC(agentId) + `/datasources/${datasourceId}/tables`);
        return response.data;
    }

    // todo: 后端接口返回的数据类型
    async initAgentSchema(agentId: string, datasourceId: string, tables: string[]): Promise<Object> {
        const response = await axios.post(BASE_URL_FUNC(agentId) + `/init`,
            { datasourceId: datasourceId, tables: tables});
        return response.data;
    }

}

export default new AgentSchemaService()
