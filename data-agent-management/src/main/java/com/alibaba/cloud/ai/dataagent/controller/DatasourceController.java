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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.datasource.DatasourceTypeDTO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/datasource")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class DatasourceController {

	private final DatasourceService datasourceService;

	@GetMapping("/types")
	public ApiResponse<List<DatasourceTypeDTO>> getDatasourceTypes() {
		List<BizDataSourceTypeEnum> standardTypes = Arrays.asList(BizDataSourceTypeEnum.MYSQL,
				BizDataSourceTypeEnum.POSTGRESQL, BizDataSourceTypeEnum.DAMENG, BizDataSourceTypeEnum.SQL_SERVER,
				BizDataSourceTypeEnum.ORACLE, BizDataSourceTypeEnum.HIVE);

		List<DatasourceTypeDTO> types = standardTypes.stream()
			.map(type -> DatasourceTypeDTO.builder()
				.code(type.getCode())
				.typeName(type.getTypeName())
				.dialect(type.getDialect())
				.protocol(type.getProtocol())
				.displayName(type.getDialect())
				.build())
			.collect(Collectors.toList());

		return ApiResponse.success("获取数据源类型成功", types);
	}

	@GetMapping
	public List<Datasource> getAllDatasource(@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "type", required = false) String type) {
		if (StringUtils.isNotBlank(status)) {
			return datasourceService.getDatasourceByStatus(status);
		}
		if (StringUtils.isNotBlank(type)) {
			return datasourceService.getDatasourceByType(type);
		}
		return datasourceService.getAllDatasource();
	}

	@GetMapping("/{id}")
	public Datasource getDatasourceById(@PathVariable Integer id) {
		return checkDatasourceExists(id);
	}

	@GetMapping("/{id}/tables")
	public List<String> getDatasourceTables(@PathVariable Integer id) {
		checkDatasourceExists(id);
		try {
			return datasourceService.getDatasourceTables(id);
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@PostMapping
	public Datasource createDatasource(@RequestBody Datasource datasource) {
		try {
			return datasourceService.createDatasource(datasource);
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@PutMapping("/{id}")
	public Datasource updateDatasource(@PathVariable Integer id, @RequestBody Datasource datasource) {
		checkDatasourceExists(id);
		try {
			return datasourceService.updateDatasource(id, datasource);
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> deleteDatasource(@PathVariable Integer id) {
		try {
			checkDatasourceExists(id);
			datasourceService.deleteDatasource(id);
			return ApiResponse.success("数据源删除成功");
		}
		catch (ResponseStatusException e) {
			throw new InvalidInputException(e.getMessage());
		}
		catch (Exception e) {
			throw new InternalServerException("删除失败：" + e.getMessage());
		}
	}

	@PostMapping("/{id}/test")
	public ApiResponse<Void> testConnection(@PathVariable Integer id) {
		try {
			boolean success = datasourceService.testConnection(id);
			return success ? ApiResponse.success("连接测试成功") : ApiResponse.error("连接测试失败");
		}
		catch (Exception e) {
			throw new InternalServerException("测试失败：" + e.getMessage());
		}
	}

	@GetMapping("/{id}/tables/{tableName}/columns")
	public ApiResponse<List<String>> getTableColumns(@PathVariable Integer id, @PathVariable String tableName) {
		try {
			List<String> columns = datasourceService.getTableColumns(id, tableName);
			return ApiResponse.success("获取字段列表成功", columns);
		}
		catch (Exception e) {
			throw new InternalServerException("获取字段列表失败：" + e.getMessage());
		}
	}

	private Datasource checkDatasourceExists(Integer id) {
		Datasource datasource = datasourceService.getDatasourceById(id);
		if (datasource == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Datasource: [%s] not found".formatted(id));
		}
		return datasource;
	}

}
