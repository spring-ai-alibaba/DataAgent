/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.tools.ListTableSchemaTool;
import com.alibaba.cloud.ai.dataagent.tools.SequentialThinkingTool;
import com.alibaba.cloud.ai.dataagent.tools.SqlExecuteTool;
import com.alibaba.cloud.ai.dataagent.tools.TodoWriteTool;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class ToolFactory {

  private final SequentialThinkingTool sequentialThinkingTool;

  private final TodoWriteTool todoWriteTool;

  private final ListTableSchemaTool listTablesTool;

  private final SqlExecuteTool sqlExecuteTool;

  private List<ToolCallback> tools = new ArrayList<>();

  public List<ToolCallback> getTools() {
    if (tools == null || tools.isEmpty()) {
      // TODO 这里应该根据UI上的配置来觉得用哪些工具
      tools.add(todoWriteTool.toolCallback());
      tools.add(sequentialThinkingTool.toolCallback());
      tools.add(listTablesTool.toolCallback());
      tools.add(sqlExecuteTool.toolCallback());
    }
    return tools;
  }
}
