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

export interface GraphRequest {
  agentId: string;
  threadId?: string;
  query: string;
  humanFeedback: boolean;
  humanFeedbackContent?: string;
  rejectedPlan: boolean;
  nl2sqlOnly: boolean;
  plainReport: boolean;
}

export interface GraphNodeResponse {
  agentId: string;
  threadId: string;
  nodeName: string;
  textType: TextType;
  text: string;
  error: boolean;
  complete: boolean;
}

export enum TextType {
  JSON = 'JSON',
  PYTHON = 'PYTHON',
  SQL = 'SQL',
  HTML = 'HTML',
  MARK_DOWN = 'MARK_DOWN',
  RESULT_SET = 'RESULT_SET',
  TEXT = 'TEXT',
}

const API_BASE_URL = '/api';

class GraphService {
  /**
   * 流式搜索处理
   * @param request 图请求参数
   * @param onMessage 消息回调函数
   * @param onError 错误回调函数
   * @param onComplete 完成回调函数
   * @returns 关闭连接的函数
   */
  async streamSearch(
    request: GraphRequest,
    onMessage: (response: GraphNodeResponse) => Promise<void>,
    onError?: (error: Error) => Promise<void>,
    onComplete?: () => Promise<void>,
  ): Promise<() => void> {
    // 构建查询参数
    const params = new URLSearchParams();
    params.append('agentId', request.agentId);
    if (request.threadId) {
      params.append('threadId', request.threadId);
    }
    params.append('query', request.query);
    params.append('humanFeedback', request.humanFeedback.toString());
    params.append('rejectedPlan', request.rejectedPlan.toString());
    params.append('nl2sqlOnly', request.nl2sqlOnly.toString());
    params.append('plainReport', request.plainReport.toString());

    if (request.humanFeedbackContent) {
      params.append('humanFeedbackContent', request.humanFeedbackContent);
    }

    const url = `${API_BASE_URL}/stream/search?${params.toString()}`;

    const eventSource = new EventSource(url);

    eventSource.onmessage = async event => {
      try {
        const nodeResponse: GraphNodeResponse = JSON.parse(event.data);
        console.log(
          `Node: ${nodeResponse.nodeName}, message: ${nodeResponse.text}, type: ${nodeResponse.textType}`,
        );
        await onMessage(nodeResponse);
      } catch (parseError) {
        console.error('Failed to parse SSE data:', parseError);
        if (onError) {
          await onError(new Error('Failed to parse server response'));
        }
      }
    };

    let isCompleted = false;

    eventSource.onerror = async error => {
      // 如果已经完成，忽略错误（可能是正常关闭）
      if (isCompleted) {
        return;
      }
      console.error('EventSource error:', error);
      if (onError) {
        await onError(new Error('Stream connection failed'));
      }
      eventSource.close();
    };

    eventSource.addEventListener('complete', async () => {
      isCompleted = true;
      if (onComplete) {
        await onComplete();
      }
      eventSource.close();
    });

    // 返回关闭函数，允许外部控制
    return () => {
      eventSource.close();
    };
  }
}

export default new GraphService();
