<!--
 * Copyright 2025 the original author or authors.
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
-->

<template>
  <BaseLayout>
    <el-container style="height: calc(100vh - 60px); gap: 0">
      <!-- 左侧历史消息栏 -->
      <ChatSessionSidebar
        :agent="agent"
        :handleSetCurrentSession="
          async (session: ChatSession | null) => {
            currentSession = session;
            await selectSession(session);
          }
        "
        :handleGetCurrentSession="
          () => {
            return currentSession;
          }
        "
        :handleSelectSession="selectSession"
        :handleDeleteSessionState="deleteSessionState"
      />

      <!-- 右侧对话栏 -->
      <el-main style="background-color: white; display: flex; flex-direction: column">
        <!-- 消息显示区域 -->
        <div class="chat-container" ref="chatContainer">
          <div v-if="!currentSession" class="empty-state">
            <el-empty description="请选择一个会话或创建新会话开始对话" />
            <PresetQuestions
              v-if="agent.id"
              :agentId="agent.id"
              :onQuestionClick="handlePresetQuestionClick"
              class="empty-state-preset"
            />
          </div>
          <div v-else class="messages-area">
            <div
              v-for="message in currentMessages"
              :key="message.id"
              :class="message.messageType === 'text' ? ['message-container', message.role] : ''"
            >
              <!-- HTML类型消息直接渲染 -->
              <div v-if="message.messageType === 'html'" v-html="message.content"></div>
              <div v-else-if="message.messageType === 'html-report'" class="html-report-message">
                <div class="report-info">
                  <el-icon><Document /></el-icon>
                  <span>HTML报告已生成</span>
                </div>
                <el-button
                  type="primary"
                  size="large"
                  @click="downloadHtmlReportFromMessage(`${message.content}`)"
                >
                  <el-icon><Download /></el-icon>
                  下载HTML报告
                </el-button>
              </div>
              <div
                v-else-if="message.messageType === 'markdown-report'"
                class="html-report-message"
              >
                <div class="report-info">
                  <el-icon><Document /></el-icon>
                  <span>Markdown 报告已生成</span>
                </div>
                <el-button
                  type="primary"
                  size="large"
                  @click="downloadMarkdownReportFromMessage(`${message.content}`)"
                >
                  <el-icon><Download /></el-icon>
                  下载Markdown报告
                </el-button>
              </div>
              <!-- 文本类型消息使用原有布局 -->
              <div v-else :class="['message', message.role]">
                <div class="message-avatar">
                  <el-avatar :size="32">
                    {{ message.role === 'user' ? '我' : 'AI' }}
                  </el-avatar>
                </div>
                <div class="message-content">
                  <div class="message-text" v-html="formatMessageContent(message)"></div>
                </div>
              </div>
            </div>

            <!-- 流式响应显示区域 -->
            <div v-if="isStreaming" class="streaming-response">
              <div class="streaming-header">
                <el-icon class="loading-icon"><Loading /></el-icon>
                <span>智能体正在处理中...</span>
              </div>
              <div class="agent-response-container">
                <div
                  v-for="(nodeBlock, index) in nodeBlocks"
                  :key="index"
                  v-html="generateNodeHtml(nodeBlock)"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 人类反馈区域 -->
        <HumanFeedback
          v-if="showHumanFeedback"
          :request="lastRequest"
          :handleFeedback="handleHumanFeedback"
        />

        <!-- 预设问题区域 -->
        <PresetQuestions
          v-if="currentSession && agent.id"
          :agentId="agent.id"
          :onQuestionClick="handlePresetQuestionClick"
        />

        <!-- 输入区域 -->
        <div class="input-area" v-if="currentSession">
          <div class="input-controls">
            <div class="switch-group">
              <div class="switch-item">
                <span class="switch-label">人工反馈</span>
                <el-tooltip
                  :disabled="!requestOptions.nl2sqlOnly"
                  content="该功能在NL2SQL模式下不能使用"
                  placement="top"
                >
                  <el-switch
                    v-model="requestOptions.humanFeedback"
                    :disabled="requestOptions.nl2sqlOnly || isStreaming || showHumanFeedback"
                  />
                </el-tooltip>
              </div>
              <div class="switch-item">
                <span class="switch-label">仅NL2SQL</span>
                <el-switch
                  v-model="requestOptions.nl2sqlOnly"
                  :disabled="isStreaming || showHumanFeedback"
                  @change="handleNl2sqlOnlyChange"
                />
              </div>
              <div class="switch-item">
                <span class="switch-label">简洁报告</span>
                <el-tooltip
                  :disabled="!requestOptions.nl2sqlOnly"
                  content="该功能在NL2SQL模式下不能使用"
                  placement="top"
                >
                  <el-switch
                    v-model="requestOptions.plainReport"
                    :disabled="requestOptions.nl2sqlOnly || isStreaming || showHumanFeedback"
                  />
                </el-tooltip>
              </div>
              <div class="switch-item">
                <span class="switch-label">自动Scroll</span>
                <el-switch v-model="autoScroll" />
              </div>
              <div class="switch-item">
                <span class="switch-label">显示SQL结果</span>
                <el-tooltip
                  content="启用本功能会将SQL查询结果存储到DataAgent项目的数据库中，如果数据量较大不建议开启本功能"
                  placement="top"
                >
                  <el-switch
                    v-model="resultSetDisplayConfig.showSqlResults"
                    :disabled="isStreaming || showHumanFeedback"
                  />
                </el-tooltip>
              </div>
              <div class="switch-item">
                <span class="switch-label">每页数量</span>
                <el-select
                  v-model="resultSetDisplayConfig.pageSize"
                  :disabled="isStreaming || showHumanFeedback"
                  style="width: 80px"
                >
                  <el-option label="5" :value="5" />
                  <el-option label="10" :value="10" />
                  <el-option label="20" :value="20" />
                  <el-option label="50" :value="50" />
                  <el-option label="100" :value="100" />
                </el-select>
              </div>
            </div>
          </div>
          <div class="input-container">
            <el-input
              v-model="userInput"
              type="textarea"
              :rows="3"
              placeholder="请输入您的问题..."
              :disabled="isStreaming || showHumanFeedback"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <el-button
              v-if="!isStreaming"
              type="primary"
              @click="sendMessage"
              :disabled="showHumanFeedback"
              circle
              class="send-button"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
            <el-button
              v-else
              type="danger"
              @click="stopStreaming"
              circle
              class="send-button stop-button-inline"
            >
              <el-icon><CircleClose /></el-icon>
            </el-button>
          </div>
        </div>
      </el-main>
    </el-container>
  </BaseLayout>
</template>

<script lang="ts">
  import { ref, defineComponent, onMounted, nextTick, computed } from 'vue';
  import { useRoute } from 'vue-router';
  import { ElMessage } from 'element-plus';
  import { Loading, Promotion, Document, Download, CircleClose } from '@element-plus/icons-vue';
  import hljs from 'highlight.js';
  import { marked } from 'marked';
  import DOMPurify from 'dompurify';
  import 'highlight.js/styles/github.css';
  // 导入并注册语言
  import sql from 'highlight.js/lib/languages/sql';
  import python from 'highlight.js/lib/languages/python';
  import json from 'highlight.js/lib/languages/json';

  // 注册语言
  hljs.registerLanguage('sql', sql);
  hljs.registerLanguage('python', python);
  hljs.registerLanguage('json', json);
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import AgentService from '@/services/agent';
  import ChatService, { type ChatSession, type ChatMessage } from '@/services/chat';
  import GraphService, {
    type GraphRequest,
    type GraphNodeResponse,
    TextType,
  } from '@/services/graph';
  import { type Agent } from '@/services/agent';
  import { type ResultSetData, type ResultSetDisplayConfig } from '@/services/resultSet';
  import { SessionRuntimeState, useSessionStateManager } from '@/services/sessionStateManager';
  import HumanFeedback from '@/components/run/HumanFeedback.vue';
  import ChatSessionSidebar from '@/components/run/ChatSessionSidebar.vue';
  import PresetQuestions from '@/components/run/PresetQuestions.vue';

  // 扩展Window接口以包含自定义方法
  declare global {
    interface Window {
      copyTextToClipboard: (btn: HTMLElement) => void;
      handleResultSetPagination: (btn: HTMLElement, direction: 'prev' | 'next') => void;
    }
  }

  export default defineComponent({
    name: 'AgentRun',
    components: {
      BaseLayout,
      Loading,
      Promotion,
      Document,
      Download,
      CircleClose,
      HumanFeedback,
      ChatSessionSidebar,
      PresetQuestions,
    },
    created() {
      window.copyTextToClipboard = btn => {
        const text = btn.previousElementSibling.textContent;
        const originalText = btn.textContent;

        navigator.clipboard
          .writeText(text)
          .then(() => {
            btn.textContent = '已复制!';
            setTimeout(() => {
              btn.textContent = originalText;
            }, 3000);
          })
          .catch(() => {
            btn.textContent = '复制失败';
            setTimeout(() => {
              btn.textContent = originalText;
            }, 3000);
          });
      };

      // 结果集翻页事件处理
      window.handleResultSetPagination = (btn: HTMLElement, direction: 'prev' | 'next') => {
        const container = btn.closest('.result-set-container');
        if (!container) return;

        const currentPageElement = container.querySelector('.result-set-current-page');
        const prevBtn = container.querySelector('.result-set-pagination-prev') as HTMLButtonElement;
        const nextBtn = container.querySelector('.result-set-pagination-next') as HTMLButtonElement;
        const pages = container.querySelectorAll('.result-set-page');

        if (!currentPageElement || !prevBtn || !nextBtn || pages.length === 0) return;

        let currentPage = parseInt(currentPageElement.textContent || '1');
        const totalPages = pages.length;

        if (direction === 'prev' && currentPage > 1) {
          currentPage--;
        } else if (direction === 'next' && currentPage < totalPages) {
          currentPage++;
        }

        // 更新页面显示
        pages.forEach((page: Element) => {
          page.classList.remove('result-set-page-active');
        });
        const targetPage = container.querySelector(`.result-set-page[data-page="${currentPage}"]`);
        if (targetPage) {
          targetPage.classList.add('result-set-page-active');
        }

        // 更新页码显示
        currentPageElement.textContent = currentPage.toString();

        // 更新按钮状态
        prevBtn.disabled = currentPage === 1;
        nextBtn.disabled = currentPage === totalPages;
      };
    },
    setup() {
      const route = useRoute();

      // 响应式数据
      const agent = ref<Agent>({} as Agent);
      const currentSession = ref<ChatSession | null>(null);
      const currentMessages = ref<ChatMessage[]>([]);
      const userInput = ref('');
      const { getSessionState, syncStateToView, saveViewToState, deleteSessionState } =
        useSessionStateManager();
      const isStreaming = ref(false);
      const nodeBlocks = ref<GraphNodeResponse[][]>([]);
      const requestOptions = ref({
        humanFeedback: false,
        nl2sqlOnly: false,
        plainReport: false,
      });

      // 监听NL2SQL开关变化
      const handleNl2sqlOnlyChange = (value: boolean) => {
        if (value) {
          // 当仅NL2SQL开启时，禁用人工反馈和简洁报告，并设为false
          requestOptions.value.humanFeedback = false;
          requestOptions.value.plainReport = false;
        }
      };
      const autoScroll = ref(true);
      const chatContainer = ref<HTMLElement | null>(null);

      // 人工反馈相关数据
      const showHumanFeedback = ref(false);
      const lastRequest = ref<GraphRequest | null>(null);

      // 结果集显示配置
      const resultSetDisplayConfig = ref<ResultSetDisplayConfig>({
        showSqlResults: false,
        pageSize: 20,
      });

      const agentId = computed(() => route.params.id as string);

      const loadAgent = async () => {
        try {
          const agentData = await AgentService.get(parseInt(agentId.value));
          if (agentData) {
            agent.value = agentData;
          } else {
            throw new Error('Agent 不存在');
          }
        } catch (error) {
          ElMessage.error('加载Agent失败');
          console.error('加载Agent失败:', error);
        }
      };

      const selectSession = async (session: ChatSession | null) => {
        // 将源会话状态保存，然后切换到目标会话
        if (currentSession.value) {
          saveViewToState(currentSession.value.id, { isStreaming, nodeBlocks });
        }
        currentSession.value = session;

        try {
          if (session === null) {
            currentMessages.value = [];
            nodeBlocks.value = [];
            isStreaming.value = false;
            return;
          }
          syncStateToView(session.id, { isStreaming, nodeBlocks });
          currentMessages.value = await ChatService.getSessionMessages(session.id);
          scrollToBottom();
        } catch (error) {
          ElMessage.error('加载消息失败');
          console.error('加载消息失败:', error);
        }
      };

      const sendMessage = async () => {
        if (!userInput.value.trim()) {
          ElMessage.warning('请输入请求消息！');
          return;
        }
        if (!currentSession.value || isStreaming.value) {
          ElMessage.warning('智能体正在处理中，请稍后...');
          return;
        }

        const needsTitle = !currentSession.value?.title || currentSession.value.title === '新会话';

        const userMessage: ChatMessage = {
          sessionId: currentSession.value.id,
          role: 'user',
          content: userInput.value,
          messageType: 'text',
          titleNeeded: needsTitle,
        };
        try {
          // 保存用户消息
          const savedMessage = await ChatService.saveMessage(currentSession.value.id, userMessage);
          currentMessages.value.push(savedMessage);
          const sessionState = getSessionState(currentSession.value.id);

          const request: GraphRequest = {
            agentId: agentId.value,
            query: userInput.value,
            humanFeedback: requestOptions.value.humanFeedback,
            nl2sqlOnly: requestOptions.value.nl2sqlOnly,
            plainReport: requestOptions.value.plainReport,
            rejectedPlan: false,
            humanFeedbackContent: null,
            threadId: sessionState.lastRequest?.threadId || null,
          };

          userInput.value = '';

          await sendGraphRequest(request, true);
        } catch (error) {
          ElMessage.error('未知错误');
          console.error(error);
        }
      };

      const sendGraphRequest = async (request: GraphRequest, rejectedPlan: boolean) => {
        const sessionId = currentSession.value!.id;
        const sessionTitle = currentSession.value!.title;
        const sessionState = getSessionState(sessionId);
        try {
          lastRequest.value = request;
          // 准备流式请求
          isStreaming.value = true;
          nodeBlocks.value = [];

          let currentNodeName: string | null = null;
          let currentBlockIndex: number = -1;
          const pendingSavePromises: Promise<void>[] = [];

          // 重置报告状态
          resetReportState(sessionState, request);

          const saveNodeMessage = (node: GraphNodeResponse[]): Promise<void> => {
            if (!node || !node.length) return Promise.resolve();

            // 使用generateNodeHtml方法生成HTML代码，确保显示与保存一致
            const nodeHtml = generateNodeHtml(node);

            const aiMessage: ChatMessage = {
              sessionId,
              role: 'assistant',
              content: nodeHtml,
              messageType: 'html',
            };

            return ChatService.saveMessage(sessionId, aiMessage).catch(error => {
              console.error('保存AI消息失败:', error);
            });
          };

          // 发送流式请求
          const closeStream = await GraphService.streamSearch(
            request,
            (response: GraphNodeResponse) => {
              if (response.error) {
                ElMessage.error(`处理错误: ${response.text}`);
                return;
              }

              if (sessionState.lastRequest) {
                sessionState.lastRequest.threadId = response.threadId;
              }

              // 检查是否是报告节点
              if (response.nodeName === 'ReportGeneratorNode') {
                const isNewNode: boolean =
                  currentNodeName === null || response.nodeName !== currentNodeName;

                if (isNewNode) {
                  // 保存上一个节点的消息（如果有）
                  if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                    const savePromise = saveNodeMessage(sessionState.nodeBlocks[currentBlockIndex]);
                    pendingSavePromises.push(savePromise);
                  }

                  // 创建新的节点块
                  const newBlock: GraphNodeResponse = {
                    ...response,
                    text: response.text,
                  };
                  sessionState.nodeBlocks.push([newBlock]);
                  currentBlockIndex = sessionState.nodeBlocks.length - 1;
                  currentNodeName = response.nodeName;
                }
                // 处理HTML报告
                if (response.textType === 'HTML') {
                  sessionState.htmlReportContent += response.text;
                  sessionState.htmlReportSize = sessionState.htmlReportContent.length;

                  // 更新显示：当前已经收集了多少字节的报告
                  const reportNode: GraphNodeResponse[] = sessionState.nodeBlocks.find(
                    (block: GraphNodeResponse[]) =>
                      block.length > 0 &&
                      block[0].nodeName === 'ReportGeneratorNode' &&
                      block[0].textType === 'HTML',
                  );
                  if (reportNode) {
                    reportNode[0].text = `正在收集HTML报告... 已收集 ${sessionState.htmlReportSize} 字节`;
                  } else {
                    sessionState.nodeBlocks.push([
                      {
                        ...response,
                        text: `正在收集HTML报告... 已收集 ${sessionState.htmlReportSize} 字节`,
                      },
                    ]);
                  }
                }
                // 处理Markdown报告
                else if (response.textType === 'MARK_DOWN') {
                  sessionState.markdownReportContent += response.text;
                  const reportNode: GraphNodeResponse[] = sessionState.nodeBlocks.find(
                    (block: GraphNodeResponse[]) =>
                      block.length > 0 &&
                      block[0].nodeName === 'ReportGeneratorNode' &&
                      block[0].textType === 'MARK_DOWN',
                  );
                  if (reportNode) {
                    reportNode[0].text = `正在收集Markdown报告... 已收集 ${sessionState.markdownReportContent.length} 字节`;
                  } else {
                    sessionState.nodeBlocks.push([
                      {
                        ...response,
                        text: `正在收集Markdown报告... 已收集 ${sessionState.markdownReportContent.length} 字节`,
                      },
                    ]);
                  }
                }
              } else {
                // 处理其他节点（同步处理逻辑）
                const isNewNode: boolean =
                  currentNodeName === null || response.nodeName !== currentNodeName;

                if (isNewNode) {
                  // 保存上一个节点的消息（如果有）
                  if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                    const savePromise = saveNodeMessage(sessionState.nodeBlocks[currentBlockIndex]);
                    pendingSavePromises.push(savePromise);
                  }

                  // 创建新的节点块
                  const newBlock: GraphNodeResponse = {
                    ...response,
                    text: response.text,
                  };
                  sessionState.nodeBlocks.push([newBlock]);
                  currentBlockIndex = sessionState.nodeBlocks.length - 1;
                  currentNodeName = response.nodeName;
                } else {
                  // 继续当前节点的内容
                  if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                    const newBlock: GraphNodeResponse = {
                      ...response,
                      text: response.text,
                    };
                    sessionState.nodeBlocks[currentBlockIndex].push(newBlock);
                  } else {
                    // 创建新的节点块
                    const newBlock: GraphNodeResponse = {
                      ...response,
                      text: response.text,
                    };
                    sessionState.nodeBlocks.push([newBlock]);
                    currentBlockIndex = sessionState.nodeBlocks.length - 1;
                    currentNodeName = response.nodeName;
                  }
                }
              }

              // 如果是当前显示的会话，同步到视图并滚动
              if (currentSession.value?.id === sessionId) {
                nodeBlocks.value = sessionState.nodeBlocks;
                if (autoScroll.value) {
                  scrollToBottom();
                }
              }
            },
            async (error: Error) => {
              ElMessage.error(`流式请求失败: ${error.message}`);
              console.error('error: ' + error);
              // 等待所有待处理的保存操作完成
              if (pendingSavePromises.length > 0) {
                await Promise.all(pendingSavePromises);
              }
              sessionState.isStreaming = false;
              sessionState.closeStream = null;
              currentNodeName = null;
              // 出错时只有当前会话才重新加载
              if (currentSession.value?.id === sessionId) {
                isStreaming.value = false;
                await selectSession(currentSession.value);
              }
            },
            async () => {
              // 等待所有待处理的保存操作完成
              if (pendingSavePromises.length > 0) {
                await Promise.all(pendingSavePromises);
              }

              // 保存报告到后端
              if (sessionState.htmlReportContent) {
                const htmlReportMessage: ChatMessage = {
                  sessionId,
                  role: 'assistant',
                  content: sessionState.htmlReportContent,
                  messageType: 'html-report',
                };

                await ChatService.saveMessage(sessionId, htmlReportMessage)
                  .then(savedMessage => {
                    if (currentSession.value?.id === sessionId) {
                      currentMessages.value.push(savedMessage);
                    }
                  })
                  .catch(error => {
                    ElMessage.error('保存HTML报告失败！');
                    console.error('保存HTML报告失败:', error);
                  });
                // 对话的HTML报告保存后结束流式响应，并判断是否需要同步页面
                sessionState.isStreaming = false;
                if (currentSession.value?.id === sessionId) {
                  isStreaming.value = false;
                  nodeBlocks.value = [];
                }
              } else if (sessionState.markdownReportContent) {
                const markdownMessage: ChatMessage = {
                  sessionId,
                  role: 'assistant',
                  content: sessionState.markdownReportContent,
                  messageType: 'markdown-report',
                };

                await ChatService.saveMessage(sessionId, markdownMessage)
                  .then(savedMessage => {
                    if (currentSession.value?.id === sessionId) {
                      currentMessages.value.push(savedMessage);
                    }
                  })
                  .catch(error => {
                    console.error('保存Markdown报告失败:', error);
                  });

                sessionState.isStreaming = false;
                if (currentSession.value?.id === sessionId) {
                  isStreaming.value = false;
                  nodeBlocks.value = [];
                }
              } else {
                // 其他节点，可能是错误或人类反馈模式
                // 保存最后一个节点的消息（如果有）
                if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                  await saveNodeMessage(sessionState.nodeBlocks[currentBlockIndex]);
                }

                // 如果是人工反馈模式，显示反馈组件
                if (requestOptions.value.humanFeedback && rejectedPlan) {
                  showHumanFeedback.value = true;
                } else {
                  // 所有节点处理完成
                  sessionState.isStreaming = false;
                  // 如果是当前显示的会话，同步到视图
                  if (currentSession.value?.id === sessionId) {
                    isStreaming.value = false;
                  }
                }
              }

              ElMessage.success(`会话[${sessionTitle}]处理完成`);
              currentNodeName = null;
              closeStream();
              // 只有当前会话才重新加载消息
              if (currentSession.value?.id === sessionId) {
                await selectSession(currentSession.value);
              }
            },
          );
          // 保存closeStream函数到会话状态
          sessionState.closeStream = closeStream;
        } catch (error) {
          ElMessage.error('发送消息失败');
          console.error('发送消息失败:', error);
          sessionState.isStreaming = false;
          sessionState.closeStream = null;
          if (currentSession.value?.id === sessionId) {
            isStreaming.value = false;
          }
        }
      };

      const formatMessageContent = (message: ChatMessage) => {
        if (message.messageType === 'text') {
          return message.content.replace(/\n/g, '<br>');
        }
        return message.content;
      };

      // 从消息内容下载HTML报告
      const downloadHtmlReportFromMessage = (content: string) => {
        if (!content) {
          ElMessage.warning('没有可下载的HTML报告');
          return;
        }

        const blob = new Blob([content], { type: 'text/html' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `report_${new Date().getTime()}.html`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        ElMessage.success('HTML报告下载成功');
      };

      const downloadMarkdownReportFromMessage = (content: string) => {
        if (!content) {
          ElMessage.warning('没有可下载的Markdown报告');
          return;
        }

        const blob = new Blob([content], { type: 'text/markdown' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `report_${new Date().getTime()}.md`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        ElMessage.success('Markdown报告下载成功');
      };

      // 生成节点容器的HTML代码
      const generateNodeHtml = (node: GraphNodeResponse[]) => {
        const content = formatNodeContent(node);

        return `
        <div class="agent-response-block" style="display: block !important; width: 100% !important;">
          <div class="agent-response-title">${node.length > 0 ? node[0].nodeName : '空节点'}</div>
          <div class="agent-response-content">${content}</div>
        </div>
      `;
      };

      const formatNodeContent = (node: GraphNodeResponse[]) => {
        let content = '';

        for (let idx = 0; idx < node.length; idx++) {
          if (node[idx].textType === TextType.HTML) {
            content += node[idx].text;
          } else if (node[idx].textType === TextType.TEXT) {
            content += node[idx].text.replace(/\n/g, '<br>');
          } else if (
            node[idx].textType === TextType.JSON ||
            node[idx].textType === TextType.PYTHON ||
            node[idx].textType === TextType.SQL
          ) {
            let pre = '';
            let p = idx;
            for (; p < node.length; p++) {
              if (node[p].textType !== node[idx].textType) {
                break;
              }
              pre += node[p].text;
            }
            try {
              // 使用 highlight.js 进行代码高亮
              const language = node[idx].textType.toLowerCase();
              const highlighted = hljs.highlight(pre, { language });
              content += `<pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">${language}</span><span hidden>${pre}</span><button onclick='copyTextToClipboard(this)' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs ${language}">${highlighted.value}</code></pre>`;
            } catch (error) {
              // 如果高亮失败，返回原始代码
              content += `<pre><code>${pre}</code></pre>`;
            }
            if (p < node.length) {
              idx = p - 1;
            } else {
              break;
            }
          } else if (node[idx].textType === TextType.MARK_DOWN) {
            let markdown = '';
            let p = idx;
            for (; p < node.length; p++) {
              if (node[p].textType !== TextType.MARK_DOWN) {
                break;
              }
              markdown += node[p].text;
            }

            const safeHtml = markdownToHtml(markdown);
            content += `<div class="markdown-report">${safeHtml}</div>`;

            if (p < node.length) {
              idx = p - 1;
            } else {
              break;
            }
          } else if (node[idx].textType === TextType.RESULT_SET) {
            // 渲染结果集
            if (!resultSetDisplayConfig.value.showSqlResults) {
              // 如果用户关闭了显示SQL结果，直接忽略这个节点
              continue;
            }

            try {
              // 解析JSON字符串
              const resultSetData: ResultSetData = JSON.parse(node[idx].text);

              // 检查是否有错误信息
              if (resultSetData.errorMsg) {
                content += `<div class="result-set-error">错误: ${resultSetData.errorMsg}</div>`;
                continue;
              }

              // 检查数据是否为空
              if (
                !resultSetData.column ||
                resultSetData.column.length === 0 ||
                !resultSetData.data ||
                resultSetData.data.length === 0
              ) {
                content += `<div class="result-set-empty">查询结果为空</div>`;
                continue;
              }

              // 生成表格HTML
              const tableHtml = generateResultSetTable(
                resultSetData,
                resultSetDisplayConfig.value.pageSize,
              );
              content += tableHtml;
            } catch (error) {
              console.error('解析结果集JSON失败:', error);
              content += `<div class="result-set-error">解析结果集数据失败: ${error.message}</div>`;
            }
          } else {
            console.warn(`不支持的 textType: ${node[idx].textType}`);
            content += node[idx].text;
          }
        }

        return content;
      };

      // Markdown转HTML
      const markdownToHtml = (markdown: string): string => {
        if (!markdown) return '';
        // marked 默认会转为字符串，这里仅做必要的配置
        marked.setOptions({ gfm: true, breaks: true });
        const rawHtml = marked.parse(markdown) as string;
        return DOMPurify.sanitize(rawHtml);
      };

      // 重置报告状态
      const resetReportState = (sessionState: SessionRuntimeState, request: GraphRequest) => {
        sessionState.isStreaming = true;
        sessionState.nodeBlocks = [];
        sessionState.lastRequest = request;
        sessionState.htmlReportContent = '';
        sessionState.htmlReportSize = 0;
        sessionState.markdownReportContent = '';
      };

      const scrollToBottom = () => {
        nextTick(() => {
          if (chatContainer.value) {
            chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
          }
        });
      };

      const handleHumanFeedback = async (
        request: GraphRequest,
        rejectedPlan: boolean,
        content: string,
      ) => {
        content = content.trim() || 'Accept';
        showHumanFeedback.value = false;
        const newRequest: GraphRequest = { ...request };
        newRequest.rejectedPlan = rejectedPlan;
        newRequest.humanFeedbackContent = content;
        await sendGraphRequest(newRequest, rejectedPlan);
      };

      // 处理预设问题点击
      const handlePresetQuestionClick = async (question: string) => {
        if (isStreaming.value) {
          ElMessage.warning('智能体正在处理中，请稍后...');
          return;
        }

        // 如果没有会话，先创建新会话
        if (!currentSession.value) {
          try {
            const newSession = await ChatService.createSession(parseInt(agentId.value), '新会话');
            currentSession.value = newSession;
            ElMessage.success('新会话创建成功');
          } catch (error) {
            ElMessage.error('创建会话失败');
            return;
          }
        }

        userInput.value = question;
        // 自动发送消息
        nextTick(() => {
          sendMessage();
        });
      };

      // 停止流式响应
      const stopStreaming = async () => {
        if (!currentSession.value) {
          ElMessage.warning('当前没有活动的会话');
          return;
        }

        const sessionId = currentSession.value.id;
        const sessionState = getSessionState(sessionId);

        try {
          // 检查是否有活动的流式连接
          if (!sessionState.closeStream) {
            ElMessage.warning('没有正在进行的对话');
            return;
          }

          // 关闭 EventSource 连接
          sessionState.closeStream();
          sessionState.closeStream = null;

          // 保存已接收的节点消息
          if (sessionState.nodeBlocks && sessionState.nodeBlocks.length > 0) {
            const saveNodeMessage = (node: GraphNodeResponse[]): Promise<void> => {
              if (!node || !node.length) return Promise.resolve();

              const nodeHtml = generateNodeHtml(node);

              const aiMessage: ChatMessage = {
                sessionId,
                role: 'assistant',
                content: nodeHtml,
                messageType: 'html',
              };

              return ChatService.saveMessage(sessionId, aiMessage).catch(error => {
                console.error('保存AI消息失败:', error);
              });
            };

            // 保存所有未保存的节点块
            const savePromises = sessionState.nodeBlocks.map(block => saveNodeMessage(block));
            await Promise.all(savePromises).catch(error => {
              console.error('保存节点消息时出错:', error);
            });
          }

          // 清理流式状态
          sessionState.isStreaming = false;
          sessionState.nodeBlocks = [];
          sessionState.htmlReportContent = '';
          sessionState.htmlReportSize = 0;
          sessionState.markdownReportContent = '';

          // 如果是当前显示的会话，同步更新视图
          if (currentSession.value?.id === sessionId) {
            isStreaming.value = false;
            nodeBlocks.value = [];
          }

          // 重新加载会话消息以刷新显示
          await selectSession(currentSession.value);

          ElMessage.success('已停止对话');
        } catch (error) {
          console.error('停止对话时出错:', error);
          ElMessage.error('停止对话失败');
          // 确保状态清理总是执行
          sessionState.isStreaming = false;
          sessionState.closeStream = null;
          if (currentSession.value?.id === sessionId) {
            isStreaming.value = false;
            nodeBlocks.value = [];
          }
        }
      };

      // 生成结果集表格HTML
      const generateResultSetTable = (resultSetData: ResultSetData, pageSize: number): string => {
        const columns = resultSetData.column || [];
        const allData = resultSetData.data || [];
        const total = allData.length;

        // 分页逻辑 - 生成所有页面的HTML，通过CSS控制显示
        const totalPages = Math.ceil(total / pageSize);

        let tableHtml = `<div class="result-set-container"><div class="result-set-header"><div class="result-set-info"><span>查询结果 (共 ${total} 条记录)</span><div class="result-set-pagination-controls"><span class="result-set-pagination-info">第 <span class="result-set-current-page">1</span> 页，共 ${totalPages} 页</span><div class="result-set-pagination-buttons"><button class="result-set-pagination-btn result-set-pagination-prev" onclick="handleResultSetPagination(this, 'prev')" disabled>上一页</button><button class="result-set-pagination-btn result-set-pagination-next" onclick="handleResultSetPagination(this, 'next')" ${totalPages > 1 ? '' : 'disabled'}>下一页</button></div></div></div></div><div class="result-set-table-container">`;

        // 生成所有页面的表格
        for (let page = 1; page <= totalPages; page++) {
          const startIndex = (page - 1) * pageSize;
          const endIndex = Math.min(startIndex + pageSize, total);
          const currentPageData = allData.slice(startIndex, endIndex);

          tableHtml += `<div class="result-set-page ${page === 1 ? 'result-set-page-active' : ''}" data-page="${page}"><table class="result-set-table"><thead><tr>`;

          // 添加表头
          columns.forEach(column => {
            tableHtml += `<th>${escapeHtml(column)}</th>`;
          });

          tableHtml += `</tr></thead><tbody>`;

          // 添加表格数据
          if (currentPageData.length === 0) {
            tableHtml += `<tr><td colspan="${columns.length}" class="result-set-empty-cell">暂无数据</td></tr>`;
          } else {
            currentPageData.forEach(row => {
              tableHtml += `<tr>`;
              columns.forEach(column => {
                const value = row[column] || '';
                tableHtml += `<td>${escapeHtml(value)}</td>`;
              });
              tableHtml += `</tr>`;
            });
          }

          tableHtml += `</tbody></table></div>`;
        }

        tableHtml += `</div></div>`;

        return tableHtml;
      };

      // HTML转义函数
      const escapeHtml = (text: string): string => {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
      };

      // 生命周期
      onMounted(async () => {
        await loadAgent();
      });

      return {
        agent,
        currentSession,
        currentMessages,
        userInput,
        isStreaming,
        requestOptions,
        autoScroll,
        chatContainer,
        nodeBlocks,
        agentId,
        showHumanFeedback,
        lastRequest,
        resultSetDisplayConfig,
        selectSession,
        sendMessage,
        formatMessageContent,
        formatNodeContent,
        generateNodeHtml,
        handleNl2sqlOnlyChange,
        downloadHtmlReportFromMessage,
        downloadMarkdownReportFromMessage,
        markdownToHtml,
        resetReportState,
        handleHumanFeedback,
        handlePresetQuestionClick,
        stopStreaming,
        deleteSessionState,
      };
    },
  });
</script>

<style scoped>
  /* 聊天容器样式 */
  .chat-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
    margin-bottom: 20px;
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 24px;
    padding: 40px 20px;
  }

  .empty-state-preset {
    width: 100%;
    max-width: 800px;
  }

  .messages-area {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  /* 消息容器样式 */
  .message-container {
    display: flex;
    max-width: 100%;
  }

  .message-container.user {
    justify-content: flex-end;
  }

  .message-container.assistant {
    justify-content: flex-start;
  }

  /* 消息样式 */
  .message {
    display: flex;
    gap: 12px;
    max-width: 80%;
  }

  .message.user {
    align-self: flex-end;
    flex-direction: row-reverse;
  }

  .message.assistant {
    align-self: flex-start;
  }

  .markdown-report {
    line-height: 1.6;
    color: #1f2933;
  }

  .markdown-report pre {
    background: #f6f8fa;
    padding: 10px 12px;
    border-radius: 6px;
    overflow: auto;
  }

  .message-content {
    flex: 1;
    min-width: 0;
  }

  .message-text {
    padding: 12px 16px;
    border-radius: 12px;
    line-height: 1.5;
    word-wrap: break-word;
  }

  .message.user .message-text {
    background: #409eff;
    color: white;
  }

  .message.assistant .message-text {
    background: white;
    color: #303133;
    border: 1px solid #e8e8e8;
  }

  /* 流式响应样式 */
  .streaming-response {
    background: white;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    padding: 16px;
  }

  .streaming-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }

  .loading-icon {
    animation: spin 1s linear infinite;
    color: #409eff;
  }

  .streaming-header span {
    font-weight: 500;
    color: #409eff;
  }

  .stop-button-inline {
    width: 48px;
    height: 48px;
  }

  /* 节点容器样式 */
  .agent-response-container {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .agent-response-block {
    background: #f8f9fa;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    overflow: hidden;
    transition: all 0.3s ease;
  }

  .agent-response-block:hover {
    border-color: #409eff;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  }

  .agent-response-title {
    background: #ecf5ff;
    padding: 12px 16px;
    font-weight: 600;
    color: #409eff;
    border-bottom: 1px solid #e8e8e8;
    font-size: 14px;
  }

  .agent-response-content {
    padding: 16px;
    line-height: 1.6;
    min-height: 40px;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 14px;
    white-space: pre-wrap;
    word-wrap: break-word;
  }

  .agent-response-content pre {
    margin: 0;
    background: transparent;
    border: none;
    padding: 0;
  }

  .agent-response-content code {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    background: transparent;
    padding: 0;
  }

  .node-content pre {
    margin: 0;
    white-space: pre-wrap;
    word-wrap: break-word;
  }

  /* 代码高亮样式 */
  .agent-response-content pre.hljs {
    background: #f6f8fa !important;
    border: 1px solid #e1e4e8;
    border-radius: 6px;
    padding: 16px;
    margin: 8px 0;
    overflow-x: auto;
  }

  .agent-response-content code.hljs {
    background: transparent !important;
    padding: 0;
    font-size: 13px;
    line-height: 1.45;
  }

  /* 确保高亮代码块正确显示 */
  .agent-response-content .hljs {
    display: block;
    overflow-x: auto;
    color: #24292e;
    background: #f6f8fa;
    padding: 16px;
    border-radius: 6px;
    border: 1px solid #e1e4e8;
  }

  /* HTML报告消息样式 */
  .html-report-message {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 16px;
    padding: 16px;
    background: #f8fbff;
    border-radius: 12px;
    border: 1px solid #e1f0ff;
  }

  .report-info {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #409eff;
    font-size: 16px;
    font-weight: 500;
  }

  /* 输入区域样式 */
  .input-area {
    background: white;
    border-radius: 8px;
    padding: 16px;
    border: 1px solid #e8e8e8;
  }

  .input-controls {
    margin-bottom: 12px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;
  }

  .switch-group {
    display: flex;
    gap: 20px;
    align-items: center;
  }

  .switch-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .switch-label {
    font-size: 14px;
    color: #606266;
  }

  .send-button {
    width: 48px;
    height: 48px;
  }

  .input-container {
    display: flex;
    gap: 12px;
    align-items: flex-end;
  }

  @keyframes spin {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .el-aside {
      width: 250px !important;
    }

    .message {
      max-width: 90%;
    }

    .input-container {
      flex-direction: column;
    }
  }
</style>

<style>
  /* 结果集表格样式 */
  .result-set-container {
    background: white;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    overflow: hidden;
    margin: 8px 0;
  }

  .result-set-header {
    background: #f8f9fa;
    padding: 12px 16px;
    border-bottom: 1px solid #e8e8e8;
  }

  .result-set-info {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 14px;
    color: #606266;
  }

  .result-set-pagination-controls {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .result-set-pagination-info {
    font-size: 14px;
    color: #606266;
  }

  .result-set-pagination-buttons {
    display: flex;
    gap: 8px;
  }

  .result-set-pagination-btn {
    padding: 6px 12px;
    border: 1px solid #dcdfe6;
    background: white;
    border-radius: 4px;
    font-size: 12px;
    cursor: pointer;
    transition: all 0.3s;
  }

  .result-set-pagination-btn:hover:not(:disabled) {
    background: #f5f7fa;
    border-color: #c6e2ff;
  }

  .result-set-pagination-btn:disabled {
    color: #c0c4cc;
    cursor: not-allowed;
    background: #f5f7fa;
  }

  .result-set-table-container {
    overflow-x: auto;
    position: relative;
  }

  .result-set-page {
    display: none;
  }

  .result-set-page-active {
    display: block;
  }

  .result-set-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
  }

  .result-set-table th {
    background: #f5f7fa;
    padding: 8px 12px;
    text-align: left;
    font-weight: 600;
    color: #606266;
    border-bottom: 1px solid #e8e8e8;
    white-space: nowrap;
  }

  .result-set-table td {
    padding: 8px 12px;
    border-bottom: 1px solid #f0f0f0;
    word-break: break-word;
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .result-set-table tr:hover {
    background: #f5f7fa;
  }

  .result-set-empty-cell {
    text-align: center;
    color: #909399;
    padding: 20px;
  }

  .result-set-error {
    background: #fef0f0;
    color: #f56c6c;
    padding: 8px 12px;
    border-radius: 4px;
    margin: 8px 0;
    border: 1px solid #fbc4c4;
  }

  .result-set-empty {
    background: #f4f4f5;
    color: #909399;
    padding: 8px 12px;
    border-radius: 4px;
    margin: 8px 0;
    text-align: center;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .result-set-table-container {
      font-size: 12px;
    }

    .result-set-table th,
    .result-set-table td {
      padding: 6px 8px;
    }
  }
</style>
