/*
 * Copyright 2024-2025 the original author or authors.
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

/**
 * @description 图搜索服务，处理与后端的流式 (SSE) 交互，实现搜索过程的实时反馈
 */

/**
 * @description 图搜索请求参数接口
 */
export interface GraphRequest {
  /** 智能体 ID */
  agentId: string;
  /** 聊天记忆会话 ID */
  conversationId: string;
  /** Graph 运行 ID，仅在人工反馈恢复时复用 */
  threadId?: string;
  /** 持久化对话轮次 ID，仅在人工反馈恢复时复用 */
  turnId?: string;
  /** 用户查询语句 */
  query: string;
  /** 是否需要异步生成会话标题 */
  titleNeeded?: boolean;
  /** 是否需要人工反馈 */
  humanFeedback: boolean;
  /** 人工反馈内容 */
  humanFeedbackContent?: string;
  /** 是否拒绝了之前的计划 */
  rejectedPlan: boolean;
  /** 是否仅执行 NL2SQL */
  nl2sqlOnly: boolean;
}

/**
 * @description 图节点响应数据接口
 */
export interface GraphNodeResponse {
  /** 智能体 ID */
  agentId: string;
  /** 线程 ID */
  threadId: string;
  /** 持久化对话轮次 ID */
  turnId?: string;
  /** 事件语义；历史消息可能不存在 */
  eventType?: GraphEventType;
  /** 单次节点执行 ID；历史消息可能不存在 */
  stepId?: string;
  /** 同名节点在本次运行中的执行次数 */
  attempt?: number;
  /** 当前执行的节点名称 */
  nodeName: string;
  /** 文本内容类型 */
  textType: TextType;
  /** 文本内容 */
  text: string;
  /** 是否发生错误 */
  error: boolean;
  /** 是否已完成 */
  complete: boolean;
}

export enum GraphEventType {
  NODE_OUTPUT = "NODE_OUTPUT",
  FINAL_ANSWER = "FINAL_ANSWER",
  HUMAN_FEEDBACK_REQUIRED = "HUMAN_FEEDBACK_REQUIRED",
}

/**
 * @description 文本内容类型枚举
 */
export enum TextType {
  /** JSON 格式 */
  JSON = "JSON",
  /** Python 代码 */
  PYTHON = "PYTHON",
  /** SQL 语句 */
  SQL = "SQL",
  /** HTML 内容 */
  HTML = "HTML",
  /** Markdown 内容 */
  MARK_DOWN = "MARK_DOWN",
  /** 结果集数据 */
  RESULT_SET = "RESULT_SET",
  /** 普通文本 */
  TEXT = "TEXT",
}

const API_BASE_URL = "/api";

/**
 * @description 图搜索业务逻辑处理类
 */
class GraphService {
  /**
   * @description 发起流式搜索请求 (SSE)
   * @param {GraphRequest} request - 请求参数
   * @param {(response: GraphNodeResponse) => Promise<void>} onMessage - 收到消息时的回调
   * @param {(error: Error) => Promise<void>} [onError] - 发生错误时的回调
   * @param {() => Promise<void>} [onComplete] - 搜索完成时的回调
   * @returns {Promise<(cancelRun?: boolean) => Promise<void>>} 返回一个用于关闭流连接并按需取消后端任务的函数
   */
  async streamSearch(
    request: GraphRequest,
    onMessage: (response: GraphNodeResponse) => Promise<void>,
    onError?: (error: Error) => Promise<void>,
    onComplete?: () => Promise<void>,
  ): Promise<(cancelRun?: boolean) => Promise<void>> {
    const params = new URLSearchParams();
    params.append("agentId", request.agentId);
    params.append("conversationId", request.conversationId);
    if (request.threadId) {
      params.append("threadId", request.threadId);
    }
    if (request.turnId) {
      params.append("turnId", request.turnId);
    }
    params.append("query", request.query);
    params.append("titleNeeded", Boolean(request.titleNeeded).toString());
    params.append("humanFeedback", request.humanFeedback.toString());
    params.append("rejectedPlan", request.rejectedPlan.toString());
    params.append("nl2sqlOnly", request.nl2sqlOnly.toString());

    if (request.humanFeedbackContent) {
      params.append("humanFeedbackContent", request.humanFeedbackContent);
    }

    const url = `${API_BASE_URL}/stream/search?${params.toString()}`;

    const eventSource = new EventSource(url);

    let latestThreadId = request.threadId;

    eventSource.onmessage = async (event) => {
      try {
        const nodeResponse: GraphNodeResponse = JSON.parse(event.data);
        latestThreadId = nodeResponse.threadId || latestThreadId;
        console.log(
          `Node: ${nodeResponse.nodeName}, message: ${nodeResponse.text}, type: ${nodeResponse.textType}`,
        );
        await onMessage(nodeResponse);
      } catch (parseError) {
        console.error("Failed to parse SSE data:", parseError);
        if (onError) {
          await onError(new Error("Failed to parse server response"));
        }
      }
    };

    let isCompleted = false;

    eventSource.addEventListener("error", async (event) => {
      if (!(event instanceof MessageEvent) || !event.data || isCompleted)
        return;
      isCompleted = true;
      eventSource.close();
      try {
        const response = JSON.parse(event.data) as GraphNodeResponse;
        if (onError) {
          await onError(new Error(response.text || "Stream processing failed"));
        }
      } catch {
        if (onError) await onError(new Error("Stream processing failed"));
      }
    });

    eventSource.onerror = async (_error) => {
      // If already completed or the connection was intentionally closed, ignore
      if (isCompleted) {
        return;
      }
      // EventSource.CLOSED = 2: connection was closed (normal after complete)
      if (eventSource.readyState === EventSource.CLOSED) {
        return;
      }
      console.error("EventSource error:", _error);
      if (onError) {
        await onError(new Error("Stream connection failed"));
      }
      eventSource.close();
    };

    eventSource.addEventListener("complete", async () => {
      isCompleted = true;
      eventSource.close();
      if (onComplete) {
        await onComplete();
      }
    });

    return async (cancelRun = false) => {
      isCompleted = true;
      eventSource.close();
      if (!cancelRun) return;

      const stopParams = new URLSearchParams({
        agentId: request.agentId,
        conversationId: request.conversationId,
      });
      if (latestThreadId) stopParams.append("threadId", latestThreadId);
      const response = await fetch(
        `${API_BASE_URL}/stream/stop?${stopParams.toString()}`,
        { method: "POST", keepalive: true },
      );
      if (!response.ok) {
        throw new Error(`Failed to stop graph run: HTTP ${response.status}`);
      }
    };
  }
}

export default new GraphService();
