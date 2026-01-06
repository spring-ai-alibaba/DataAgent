package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelImportItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel导入导出服务
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
	 * 生成Excel模板
	 */
	public byte[] generateTemplate() throws IOException {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("语义模型导入模板");

			// 创建标题行样式
			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);

			// 创建标题行
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < HEADERS.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(HEADERS[i]);
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, 20 * 256); // 设置列宽
			}

			// 添加示例数据
			Row exampleRow1 = sheet.createRow(1);
			exampleRow1.createCell(COL_TABLE_NAME).setCellValue("t_user");
			exampleRow1.createCell(COL_COLUMN_NAME).setCellValue("user_gender");
			exampleRow1.createCell(COL_BUSINESS_NAME).setCellValue("性别");
			exampleRow1.createCell(COL_DATA_TYPE).setCellValue("varchar");
			exampleRow1.createCell(COL_SYNONYMS).setCellValue("用户性别");
			exampleRow1.createCell(COL_BUSINESS_DESC).setCellValue("用户性别。枚举值：0=未知, 1=男, 2=女");

			Row exampleRow2 = sheet.createRow(2);
			exampleRow2.createCell(COL_TABLE_NAME).setCellValue("t_account");
			exampleRow2.createCell(COL_COLUMN_NAME).setCellValue("account_status");
			exampleRow2.createCell(COL_BUSINESS_NAME).setCellValue("账号状态");
			exampleRow2.createCell(COL_DATA_TYPE).setCellValue("varchar");
			exampleRow2.createCell(COL_SYNONYMS).setCellValue("状态");
			exampleRow2.createCell(COL_BUSINESS_DESC).setCellValue("账号生命周期状态。枚举值：0=未激活, 1=正常, 2=冻结, 3=注销");

			workbook.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * 解析Excel文件
	 */
	public List<SemanticModelImportItem> parseExcel(MultipartFile file) throws IOException {
		List<SemanticModelImportItem> items = new ArrayList<>();

		try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
			Sheet sheet = workbook.getSheetAt(0);

			// 跳过标题行，从第二行开始读取
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
	 * 获取单元格值（转换为字符串）
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
					// 避免科学计数法
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
