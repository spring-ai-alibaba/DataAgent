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
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticRelation;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticColumnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticRelationMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticTableMapper;
import com.alibaba.cloud.ai.dataagent.service.semantic.runtime.SemanticManager;
import com.alibaba.cloud.ai.dataagent.vo.PageResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StructuredSemanticServiceImpl implements StructuredSemanticService {

	private final SemanticTableMapper semanticTableMapper;

	private final SemanticColumnMapper semanticColumnMapper;

	private final SemanticRelationMapper semanticRelationMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final SemanticManager semanticManager;

	@Override
	public PageResponse<List<SemanticTable>> queryTables(SemanticTableQueryDTO queryDTO) {
		requireAgentDatasourceBinding(queryDTO.getAgentId(), queryDTO.getDatasourceId());
		List<SemanticTable> tables = semanticTableMapper.listByAgentIdAndDatasourceId(queryDTO.getAgentId(),
				queryDTO.getDatasourceId());
		List<SemanticTable> filtered = paginateSource(tables,
				table -> matchesKeyword(queryDTO.getKeyword(), table.getTableName(), table.getBusinessName(),
						table.getSynonyms(), table.getBusinessDescription(), table.getTableComment()),
				Comparator.comparing(SemanticTable::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
					.thenComparing(SemanticTable::getId, Comparator.nullsLast(Comparator.reverseOrder())),
				queryDTO.getPageNum(), queryDTO.getPageSize());
		return buildPage(filtered, tables, queryDTO.getPageNum(), queryDTO.getPageSize(),
				table -> matchesKeyword(queryDTO.getKeyword(), table.getTableName(), table.getBusinessName(),
						table.getSynonyms(), table.getBusinessDescription(), table.getTableComment()));
	}

	@Override
	@Transactional
	public SemanticTable saveTable(Long id, SemanticTableUpsertDTO dto) {
		requireAgentDatasourceBinding(dto.getAgentId(), dto.getDatasourceId());
		SemanticTable existingByName = semanticTableMapper.selectByAgentIdAndDatasourceIdAndTableName(dto.getAgentId(),
				dto.getDatasourceId(), dto.getTableName());
		if (id == null && existingByName != null) {
			throw new InvalidInputException("当前表语义已存在，请直接更新");
		}
		SemanticTable target = id == null ? new SemanticTable() : requireTable(id, dto.getAgentId(), dto.getDatasourceId());
		if (existingByName != null && !Objects.equals(existingByName.getId(), id)) {
			throw new InvalidInputException("当前表语义已存在，请直接更新");
		}
		target.setAgentId(dto.getAgentId());
		target.setDatasourceId(dto.getDatasourceId());
		target.setTableName(StringUtils.trim(dto.getTableName()));
		target.setBusinessName(trimToNull(dto.getBusinessName()));
		target.setSynonyms(trimToNull(dto.getSynonyms()));
		target.setBusinessDescription(trimToNull(dto.getBusinessDescription()));
		target.setTableComment(trimToNull(dto.getTableComment()));
		target.setIsVisible(normalizeFlag(dto.getIsVisible(), 1));
		target.setStatus(normalizeFlag(dto.getStatus(), 1));
		if (id == null) {
			semanticTableMapper.insert(target);
		}
		else {
			semanticTableMapper.updateById(target);
		}
		return semanticTableMapper.selectById(target.getId());
	}

	@Override
	@Transactional
	public void deleteTable(Long id) {
		SemanticTable target = semanticTableMapper.selectById(id);
		if (target == null) {
			throw new InvalidInputException("表语义不存在");
		}
		semanticTableMapper.deleteById(id);
	}

	@Override
	public PageResponse<List<SemanticColumn>> queryColumns(SemanticColumnQueryDTO queryDTO) {
		requireAgentDatasourceBinding(queryDTO.getAgentId(), queryDTO.getDatasourceId());
		List<SemanticColumn> columns = semanticColumnMapper.selectByAgentIdAndDatasourceIdAndTableName(
				queryDTO.getAgentId(), queryDTO.getDatasourceId(), queryDTO.getTableName());
		List<SemanticColumn> filtered = paginateSource(columns,
				column -> matchesKeyword(queryDTO.getKeyword(), column.getColumnName(), column.getBusinessName(),
						column.getSynonyms(), column.getBusinessDescription(), column.getColumnComment(),
						column.getDataType()),
				Comparator.comparing(SemanticColumn::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
					.thenComparing(SemanticColumn::getId, Comparator.nullsLast(Comparator.reverseOrder())),
				queryDTO.getPageNum(), queryDTO.getPageSize());
		return buildPage(filtered, columns, queryDTO.getPageNum(), queryDTO.getPageSize(),
				column -> matchesKeyword(queryDTO.getKeyword(), column.getColumnName(), column.getBusinessName(),
						column.getSynonyms(), column.getBusinessDescription(), column.getColumnComment(),
						column.getDataType()));
	}

	@Override
	@Transactional
	public SemanticColumn saveColumn(Long id, SemanticColumnUpsertDTO dto) {
		requireAgentDatasourceBinding(dto.getAgentId(), dto.getDatasourceId());
		SemanticColumn existingByName = semanticColumnMapper.selectByAgentIdAndDatasourceIdAndTableNameAndColumnName(
				dto.getAgentId(), dto.getDatasourceId(), dto.getTableName(), dto.getColumnName());
		if (id == null && existingByName != null) {
			throw new InvalidInputException("当前列语义已存在，请直接更新");
		}
		SemanticColumn target = id == null ? new SemanticColumn() : requireColumn(id, dto.getAgentId(), dto.getDatasourceId());
		if (existingByName != null && !Objects.equals(existingByName.getId(), id)) {
			throw new InvalidInputException("当前列语义已存在，请直接更新");
		}
		target.setAgentId(dto.getAgentId());
		target.setDatasourceId(dto.getDatasourceId());
		target.setTableName(StringUtils.trim(dto.getTableName()));
		target.setColumnName(StringUtils.trim(dto.getColumnName()));
		target.setBusinessName(trimToNull(dto.getBusinessName()));
		target.setSynonyms(trimToNull(dto.getSynonyms()));
		target.setBusinessDescription(trimToNull(dto.getBusinessDescription()));
		target.setColumnComment(trimToNull(dto.getColumnComment()));
		target.setDataType(trimToNull(dto.getDataType()));
		target.setIsVisible(normalizeFlag(dto.getIsVisible(), 1));
		target.setStatus(normalizeFlag(dto.getStatus(), 1));
		if (id == null) {
			semanticColumnMapper.insert(target);
		}
		else {
			semanticColumnMapper.updateById(target);
		}
		return semanticColumnMapper.selectById(target.getId());
	}

	@Override
	@Transactional
	public void deleteColumn(Long id) {
		SemanticColumn target = semanticColumnMapper.selectById(id);
		if (target == null) {
			throw new InvalidInputException("列语义不存在");
		}
		semanticColumnMapper.deleteById(id);
	}

	@Override
	public PageResponse<List<SemanticRelation>> queryRelations(SemanticRelationQueryDTO queryDTO) {
		requireAgentDatasourceBinding(queryDTO.getAgentId(), queryDTO.getDatasourceId());
		List<SemanticRelation> relations = semanticRelationMapper.listByAgentIdAndDatasourceId(queryDTO.getAgentId(),
				queryDTO.getDatasourceId());
		Predicate<SemanticRelation> predicate = relation -> matchesRelation(queryDTO, relation);
		List<SemanticRelation> filtered = paginateSource(relations, predicate,
				Comparator.comparing(SemanticRelation::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
					.thenComparing(SemanticRelation::getId, Comparator.nullsLast(Comparator.reverseOrder())),
				queryDTO.getPageNum(), queryDTO.getPageSize());
		return buildPage(filtered, relations, queryDTO.getPageNum(), queryDTO.getPageSize(), predicate);
	}

	@Override
	@Transactional
	public SemanticRelation saveRelation(Long id, SemanticRelationUpsertDTO dto) {
		requireAgentDatasourceBinding(dto.getAgentId(), dto.getDatasourceId());
		validateRelationColumns(dto.getSourceColumnNames(), dto.getTargetColumnNames());
		SemanticRelation target = id == null ? new SemanticRelation()
				: requireRelation(id, dto.getAgentId(), dto.getDatasourceId());
		target.setAgentId(dto.getAgentId());
		target.setDatasourceId(dto.getDatasourceId());
		target.setSourceTableName(StringUtils.trim(dto.getSourceTableName()));
		target.setSourceColumnNames(joinNormalizedColumns(dto.getSourceColumnNames()));
		target.setTargetTableName(StringUtils.trim(dto.getTargetTableName()));
		target.setTargetColumnNames(joinNormalizedColumns(dto.getTargetColumnNames()));
		target.setRelationType(trimToNull(dto.getRelationType()));
		target.setDescription(trimToNull(dto.getDescription()));
		target.setStatus(normalizeFlag(dto.getStatus(), 1));
		ensureNoDuplicateRelation(id, target);
		if (id == null) {
			semanticRelationMapper.insert(target);
		}
		else {
			semanticRelationMapper.updateById(target);
		}
		return semanticRelationMapper.selectById(target.getId());
	}

	@Override
	@Transactional
	public void deleteRelation(Long id) {
		SemanticRelation target = semanticRelationMapper.selectById(id);
		if (target == null) {
			throw new InvalidInputException("关系语义不存在");
		}
		semanticRelationMapper.deleteById(id);
	}

	private SemanticTable requireTable(Long id, Long agentId, Integer datasourceId) {
		SemanticTable table = semanticTableMapper.selectById(id);
		if (table == null || !Objects.equals(table.getAgentId(), agentId)
				|| !Objects.equals(table.getDatasourceId(), datasourceId)) {
			throw new InvalidInputException("表语义不存在");
		}
		return table;
	}

	private SemanticRelation requireRelation(Long id, Long agentId, Integer datasourceId) {
		SemanticRelation relation = semanticRelationMapper.selectById(id);
		if (relation == null || !Objects.equals(relation.getAgentId(), agentId)
				|| !Objects.equals(relation.getDatasourceId(), datasourceId)) {
			throw new InvalidInputException("关系语义不存在");
		}
		return relation;
	}

	private SemanticColumn requireColumn(Long id, Long agentId, Integer datasourceId) {
		SemanticColumn column = semanticColumnMapper.selectById(id);
		if (column == null || !Objects.equals(column.getAgentId(), agentId)
				|| !Objects.equals(column.getDatasourceId(), datasourceId)) {
			throw new InvalidInputException("列语义不存在");
		}
		return column;
	}

	private void ensureNoDuplicateRelation(Long currentId, SemanticRelation target) {
		List<SemanticRelation> relations = semanticRelationMapper.listByAgentIdAndDatasourceId(target.getAgentId(),
				target.getDatasourceId());
		boolean duplicated = relations.stream()
			.filter(relation -> !Objects.equals(relation.getId(), currentId))
			.anyMatch(relation -> sameRelation(relation, target));
		if (duplicated) {
			throw new InvalidInputException("当前关系语义已存在");
		}
	}

	private boolean sameRelation(SemanticRelation left, SemanticRelation right) {
		return semanticManager.normalizeName(left.getSourceTableName())
			.equals(semanticManager.normalizeName(right.getSourceTableName()))
				&& normalizeSignature(left.getSourceColumnNames()).equals(normalizeSignature(right.getSourceColumnNames()))
				&& semanticManager.normalizeName(left.getTargetTableName())
					.equals(semanticManager.normalizeName(right.getTargetTableName()))
				&& normalizeSignature(left.getTargetColumnNames()).equals(normalizeSignature(right.getTargetColumnNames()));
	}

	private String normalizeSignature(String columnNames) {
		return String.join(",", semanticManager.parseColumnNames(columnNames).stream().map(semanticManager::normalizeName).toList());
	}

	private void validateRelationColumns(String sourceColumnNames, String targetColumnNames) {
		List<String> source = semanticManager.parseColumnNames(sourceColumnNames);
		List<String> target = semanticManager.parseColumnNames(targetColumnNames);
		if (source.isEmpty() || target.isEmpty()) {
			throw new InvalidInputException("关系字段不能为空");
		}
		if (source.size() != target.size()) {
			throw new InvalidInputException("源字段与目标字段数量必须一致");
		}
	}

	private String joinNormalizedColumns(String columnNames) {
		return String.join(",", semanticManager.parseColumnNames(columnNames));
	}

	private void requireAgentDatasourceBinding(Long agentId, Integer datasourceId) {
		if (agentId == null) {
			throw new InvalidInputException("agentId 不能为空");
		}
		if (datasourceId == null) {
			throw new InvalidInputException("datasourceId 不能为空");
		}
		AgentDatasource binding = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (binding == null) {
			throw new InvalidInputException("未找到对应的智能体与数据源绑定关系");
		}
	}

	private boolean matchesRelation(SemanticRelationQueryDTO queryDTO, SemanticRelation relation) {
		boolean tableMatched = !StringUtils.isNotBlank(queryDTO.getTableName())
				|| semanticManager.normalizeName(relation.getSourceTableName())
					.equals(semanticManager.normalizeName(queryDTO.getTableName()))
				|| semanticManager.normalizeName(relation.getTargetTableName())
					.equals(semanticManager.normalizeName(queryDTO.getTableName()));
		if (!tableMatched) {
			return false;
		}
		return matchesKeyword(queryDTO.getKeyword(), relation.getSourceTableName(), relation.getSourceColumnNames(),
				relation.getTargetTableName(), relation.getTargetColumnNames(), relation.getRelationType(),
				relation.getDescription());
	}

	private <T> List<T> paginateSource(List<T> source, Predicate<T> predicate, Comparator<T> comparator, Integer pageNum,
			Integer pageSize) {
		int safePageNum = normalizePageNum(pageNum);
		int safePageSize = normalizePageSize(pageSize);
		return source.stream()
			.filter(predicate)
			.sorted(comparator)
			.skip((long) (safePageNum - 1) * safePageSize)
			.limit(safePageSize)
			.toList();
	}

	private <T> PageResponse<List<T>> buildPage(List<T> pageData, List<T> source, Integer pageNum, Integer pageSize,
			Predicate<T> predicate) {
		int safePageNum = normalizePageNum(pageNum);
		int safePageSize = normalizePageSize(pageSize);
		long total = source.stream().filter(predicate).count();
		int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safePageSize);
		return PageResponse.success(pageData, total, safePageNum, safePageSize, totalPages);
	}

	private int normalizePageNum(Integer pageNum) {
		return pageNum == null || pageNum < 1 ? 1 : pageNum;
	}

	private int normalizePageSize(Integer pageSize) {
		if (pageSize == null || pageSize < 1) {
			return 20;
		}
		return Math.min(pageSize, 200);
	}

	private Integer normalizeFlag(Integer flag, int defaultValue) {
		if (flag == null) {
			return defaultValue;
		}
		return flag == 0 ? 0 : 1;
	}

	private String trimToNull(String value) {
		return StringUtils.trimToNull(value);
	}

	private boolean matchesKeyword(String keyword, String... values) {
		if (!StringUtils.isNotBlank(keyword)) {
			return true;
		}
		String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
		for (String value : values) {
			if (StringUtils.isNotBlank(value) && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
				return true;
			}
		}
		return false;
	}

}
