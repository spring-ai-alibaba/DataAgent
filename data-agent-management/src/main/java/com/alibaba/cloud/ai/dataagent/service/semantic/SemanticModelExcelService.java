package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelImportItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel解析
 */
@Service
@Slf4j
public class SemanticModelExcelService {

	private static final String[] HEADERS = { "表名*", "字段名*", "业务名称*", "数据类型*", "同义词", "业务描述" };

	private static final int COL_TABLE_NAME = 0;

	private static final int COL_COLUMN_NAME = 1;

	private static final int COL_BUSINESS_NAME = 2;

	private static final int COL_DATA_TYPE = 3;

	private static final int COL_SYNONYMS = 4;

	private static final int COL_BUSINESS_DESC = 5;

	/**
	 * 解析Excel文件
	 */
	public List<SemanticModelImportItem> parseExcel(MultipartFile file) throws IOException {
		List<SemanticModelImportItem> items = new ArrayList<>();

		try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
			Sheet sheet = workbook.getSheetAt(0);

			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}

				// 检查是否为空行
				if (isEmptyRow(row)) {
					continue;
				}

				// 读取必填字段
				String tableName = getCellValueAsString(row.getCell(COL_TABLE_NAME));
				String columnName = getCellValueAsString(row.getCell(COL_COLUMN_NAME));
				String businessName = getCellValueAsString(row.getCell(COL_BUSINESS_NAME));
				String dataType = getCellValueAsString(row.getCell(COL_DATA_TYPE));

				// 验证必填字段
				if (tableName == null || tableName.trim().isEmpty()) {
					throw new IllegalArgumentException("第" + (i + 1) + "行：表名不能为空");
				}
				if (columnName == null || columnName.trim().isEmpty()) {
					throw new IllegalArgumentException("第" + (i + 1) + "行：字段名不能为空");
				}
				if (businessName == null || businessName.trim().isEmpty()) {
					throw new IllegalArgumentException("第" + (i + 1) + "行：业务名称不能为空");
				}
				if (dataType == null || dataType.trim().isEmpty()) {
					throw new IllegalArgumentException("第" + (i + 1) + "行：数据类型不能为空");
				}

				// 读取可选字段
				String synonyms = getCellValueAsString(row.getCell(COL_SYNONYMS));
				String businessDesc = getCellValueAsString(row.getCell(COL_BUSINESS_DESC));

				// 构建导入项
				SemanticModelImportItem item = SemanticModelImportItem.builder()
					.tableName(tableName.trim())
					.columnName(columnName.trim())
					.businessName(businessName.trim())
					.dataType(dataType.trim())
					.synonyms(synonyms != null ? synonyms.trim() : null)
					.businessDescription(businessDesc != null ? businessDesc.trim() : null)
					.build();

				items.add(item);
			}
		}

		if (items.isEmpty()) {
			throw new IllegalArgumentException("Excel文件中没有有效数据");
		}

		log.info("成功解析Excel文件，共{}条记录", items.size());
		return items;
	}

	/**
	 * 判断是否为空行
	 */
	private boolean isEmptyRow(Row row) {
		for (int i = 0; i < HEADERS.length; i++) {
			Cell cell = row.getCell(i);
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				String value = getCellValueAsString(cell);
				if (value != null && !value.trim().isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 获取单元格值
	 */
	private String getCellValueAsString(Cell cell) {
		if (cell == null) {
			return null;
		}

		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					return cell.getDateCellValue().toString();
				}
				else {
					return String.valueOf((long) cell.getNumericCellValue());
				}
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());
			case FORMULA:
				return cell.getCellFormula();
			case BLANK:
				return null;
			default:
				return null;
		}
	}

}
