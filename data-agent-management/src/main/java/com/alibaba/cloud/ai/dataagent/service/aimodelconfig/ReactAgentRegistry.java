package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.interceptor.ToolsInterceptor;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactAgentRegistry {

  private final DynamicModelFactory modelFactory;

  private final ModelConfigDataService modelConfigDataService;


  private final ToolFactory toolFactory;

  private final ToolsInterceptor toolsInterceptor;

  private volatile ReactAgent reactAgent;

  // =========================================================
  // 5. 构造ReactAgent
  // =========================================================
  public ReactAgent getReactAgent() {
    if (reactAgent == null) {
      synchronized (this) {
        if (reactAgent == null) {
          log.info("Initializing global ReactAgent...");
          try {
            ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
            if (config != null) {
              ChatModel chatModel = modelFactory.createChatModel(config);
              // 核心：基于新 Model 创建新 Agent，彻底消除旧参数缓存
              reactAgent = ReactAgent.builder()
                  .name("sql-agent")
                  .description(PromptHelper.buildDefaultDataAnalysisPrompt())
                  .model(chatModel)
                  .saver(new MemorySaver())
                  .tools(toolFactory.getTools())
                  .interceptors(toolsInterceptor)
                  .build();
            }
          } catch (Exception e) {
            log.error("Failed to initialize ReactAgent: {}", e.getMessage(), e);
          }
          // 兜底：如果还没初始化成功，抛出运行时异常，提示用户配置
          if (reactAgent == null) {
            throw new RuntimeException(
                "No active CHAT model configured. Please configure it in the dashboard.");
          }
        }
      }
    }
    return reactAgent;
  }
}
