package com.alibaba.cloud.ai.dataagent.dto.schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 语义模型批量导入DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticModelBatchImportDTO {

    @NotNull(message = "智能体ID不能为空")
    private Integer agentId;

    @NotEmpty(message = "导入数据不能为空")
    @Valid
    private List<SemanticModelImportItem> items;
}
