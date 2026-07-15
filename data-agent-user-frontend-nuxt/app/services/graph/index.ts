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
import { buildApiUrl } from '~/utils/api';

export interface GraphRequest {
  /** 智能体 ID */
  agentId: string;
  /** 会话线程 ID */
  threadId?: string;
  /** 用户查询语句 */
  query: string;
  /** 是否需要人工反馈 */
  humanFeedback: boolean;
  /** 人工反馈内容 */
  humanFeedbackContent?: string;
  clarificationAnswer?: string;
  resumeMode?: 'clarification' | 'human_feedback' | null;
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
  /** 当前执行的节点名称 */
  nodeName: string;
  /** 文本内容类型 */
  textType: TextType;
  /** 文本内容 */
  text: string;
  interactionType?: 'normal' | 'clarification';
  awaitingInput?: boolean;
  /** 是否发生错误 */
  error: boolean;
  /** 是否已完成 */
  complete: boolean;
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
   * @returns {Promise<() => void>} 返回一个用于手动关闭流连接的函数
   */
  async streamSearch(
    request: GraphRequest,
    onMessage: (response: GraphNodeResponse) => Promise<void>,
    onError?: (error: Error) => Promise<void>,
    onComplete?: () => Promise<void>,
  ): Promise<() => void> {
    const params = new URLSearchParams();
    params.append("agentId", request.agentId);
    if (request.threadId) {
      params.append("threadId", request.threadId);
    }
    params.append("query", request.query);
    params.append("humanFeedback", request.humanFeedback.toString());
    params.append("rejectedPlan", request.rejectedPlan.toString());
    params.append("nl2sqlOnly", request.nl2sqlOnly.toString());

    if (request.humanFeedbackContent) {
      params.append("humanFeedbackContent", request.humanFeedbackContent);
    }
    if (request.clarificationAnswer) {
      params.append("clarificationAnswer", request.clarificationAnswer);
    }
    if (request.resumeMode) {
      params.append("resumeMode", request.resumeMode);
    }

    const url = buildApiUrl(`${API_BASE_URL}/stream/search?${params.toString()}`);

    const eventSource = new EventSource(url);

    eventSource.onmessage = async (event) => {
      try {
        const nodeResponse: GraphNodeResponse = JSON.parse(event.data);
        await onMessage(nodeResponse);
      } catch (parseError) {
        console.error("Failed to parse SSE data:", parseError);
        if (onError) {
          await onError(new Error("Failed to parse server response"));
        }
      }
    };

    let isCompleted = false;
    let isFailed = false;

    eventSource.onerror = async (errorEvent) => {
      // If already completed or the connection was intentionally closed, ignore
      if (isCompleted || isFailed) {
        return;
      }
      // EventSource.CLOSED = 2: connection was closed (normal after complete)
      if (eventSource.readyState === EventSource.CLOSED) {
        return;
      }

      isFailed = true;
      eventSource.close();

      // The backend uses a named `error` SSE event for processing failures.
      // Preserve its payload instead of reporting every failure as a transport error.
      let streamError = new Error("Stream connection failed");
      if (errorEvent instanceof MessageEvent && errorEvent.data) {
        try {
          const response = JSON.parse(errorEvent.data) as GraphNodeResponse;
          streamError = new Error(response.text || "Stream processing failed");
        } catch (parseError) {
          console.error("Failed to parse SSE error data:", parseError);
        }
      }

      console.error("EventSource error:", errorEvent);
      if (onError) {
        await onError(streamError);
      }
    };

    eventSource.addEventListener("complete", async () => {
      isCompleted = true;
      eventSource.close();
      if (onComplete) {
        await onComplete();
      }
    });

    return () => {
      eventSource.close();
    };
  }
}

export default new GraphService();
