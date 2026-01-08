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

package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelImportItem;
import com.alibaba.excel.EasyExcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Excel解析服务
 */
@Service
@Slf4j
public class SemanticModelExcelService {

	/**
	 * 解析Excel文件
	 */
	public List<SemanticModelImportItem> parseExcel(MultipartFile file) throws IOException {
		log.info("开始解析Excel文件: {}", file.getOriginalFilename());

		try {
			// 使用 EasyExcel 同步读取，自动根据 @ExcelProperty 注解映射列
			List<SemanticModelImportItem> items = EasyExcel.read(file.getInputStream())
				.head(SemanticModelImportItem.class)
				.sheet()
				.doReadSync();

			if (items == null || items.isEmpty()) {
				throw new IllegalArgumentException("Excel文件中没有有效数据");
			}

			// 验证必填字段
			for (int i = 0; i < items.size(); i++) {
				SemanticModelImportItem item = items.get(i);
				int rowNum = i + 2;

				if (item.getTableName() == null || item.getTableName().trim().isEmpty()) {
					throw new IllegalArgumentException("第" + rowNum + "行：表名不能为空");
				}
				if (item.getColumnName() == null || item.getColumnName().trim().isEmpty()) {
					throw new IllegalArgumentException("第" + rowNum + "行：字段名不能为空");
				}
				if (item.getBusinessName() == null || item.getBusinessName().trim().isEmpty()) {
					throw new IllegalArgumentException("第" + rowNum + "行：业务名称不能为空");
				}
				if (item.getDataType() == null || item.getDataType().trim().isEmpty()) {
					throw new IllegalArgumentException("第" + rowNum + "行：数据类型不能为空");
				}

				// 清理字段值
				item.setTableName(item.getTableName().trim());
				item.setColumnName(item.getColumnName().trim());
				item.setBusinessName(item.getBusinessName().trim());
				item.setDataType(item.getDataType().trim());
				if (item.getSynonyms() != null) {
					item.setSynonyms(item.getSynonyms().trim());
				}
				if (item.getBusinessDescription() != null) {
					item.setBusinessDescription(item.getBusinessDescription().trim());
				}
			}

			log.info("成功解析Excel文件，共{}条记录", items.size());
			return items;
		}
		catch (Exception e) {
			log.error("解析Excel文件失败: {}", file.getOriginalFilename(), e);
			throw new IOException("解析Excel文件失败: " + e.getMessage(), e);
		}
	}

}
