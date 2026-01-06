package com.alibaba.cloud.ai.dataagent.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义模型导入项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticModelImportItem {

    @NotBlank(message = "表名不能为空")
    private String tableName;

    @NotBlank(message = "字段名不能为空")
    private String columnName;

    @NotBlank(message = "业务名称不能为空")
    private String businessName;

    private String synonyms;

    @JsonAlias({"businessDesc", "description", "desc"})
    private String businessDescription;

    private String columnComment;

    @NotBlank(message = "数据类型不能为空")
    private String dataType;
}
