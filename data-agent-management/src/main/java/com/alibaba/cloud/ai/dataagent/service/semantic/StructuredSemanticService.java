/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticColumnQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticColumnUpsertDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticRelationQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticRelationUpsertDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticTableQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticTableUpsertDTO;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticRelation;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import com.alibaba.cloud.ai.dataagent.vo.PageResponse;
import java.util.List;

public interface StructuredSemanticService {

	PageResponse<List<SemanticTable>> queryTables(SemanticTableQueryDTO queryDTO);

	SemanticTable saveTable(Long id, SemanticTableUpsertDTO dto);

	void deleteTable(Long id);

	PageResponse<List<SemanticColumn>> queryColumns(SemanticColumnQueryDTO queryDTO);

	SemanticColumn saveColumn(Long id, SemanticColumnUpsertDTO dto);

	void deleteColumn(Long id);

	PageResponse<List<SemanticRelation>> queryRelations(SemanticRelationQueryDTO queryDTO);

	SemanticRelation saveRelation(Long id, SemanticRelationUpsertDTO dto);

	void deleteRelation(Long id);
}
