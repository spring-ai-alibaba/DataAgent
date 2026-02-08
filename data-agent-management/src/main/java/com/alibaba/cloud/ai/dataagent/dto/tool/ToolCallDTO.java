package com.alibaba.cloud.ai.dataagent.dto.tool;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallDTO {
  private String toolName;
  private String input;
  private String output;
}
