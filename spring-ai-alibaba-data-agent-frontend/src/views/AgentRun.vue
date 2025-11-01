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
  <el-container style="height: calc(100vh - 60px); gap: 0;">
    <!-- 左侧历史消息栏 -->
    <ChatSessionSidebar
      :agent="agent"
      :handleSetCurrentSession="(session: ChatSession | null) => { currentSession.value = session }"
      :handleGetCurrentSession="() => { return currentSession.value }"
      :handleSelectSession="selectSession"
      :handleClearMessage="() => {currentMessages.value = []}"
    />

      <!-- 右侧对话栏 -->
      <el-main style="background-color: white; display: flex; flex-direction: column;">
        <!-- 消息显示区域 -->
        <div class="chat-container" ref="chatContainer">
          <div v-if="!currentSession" class="empty-state">
            <el-empty description="请选择一个会话或创建新会话开始对话" />
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
                <el-button type="primary" size="large" @click="downloadHtmlReportFromMessage(`${message.content}`)">
                  <el-icon><Download /></el-icon>
                  下载HTML报告
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
                  v-for="nodeBlock in nodeBlocks"
                  v-html="generateNodeHtml(nodeBlock)"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 人类反馈区域 -->
        <HumanFeedback v-if="showHumanFeedback" :request="lastRequest" :handleFeedback="handleHumanFeedback"/>

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
                    :disabled="requestOptions.nl2sqlOnly" 
                  />
                </el-tooltip>
              </div>
              <div class="switch-item">
                <span class="switch-label">仅NL2SQL</span>
                <el-switch 
                  v-model="requestOptions.nl2sqlOnly" 
                  @change="handleNl2sqlOnlyChange" 
                />
              </div>
              <div class="switch-item">
                <span class="switch-label">简洁报告</span>
                <el-tooltip
                  content="简洁报告的功能还在开发中……"
                  placement="top"
                >
                  <el-switch 
                    v-model="requestOptions.plainReport" 
                    :disabled="true" 
                  />
                </el-tooltip>
                <!-- <el-tooltip
                  :disabled="!requestOptions.nl2sqlOnly"
                  content="该功能在NL2SQL模式下不能使用"
                  placement="top"
                >
                  <el-switch 
                    v-model="requestOptions.plainReport" 
                    :disabled="requestOptions.nl2sqlOnly" 
                  />
                </el-tooltip> -->
              </div>
              <div class="switch-item">
                <span class="switch-label">自动Scroll</span>
                <el-switch v-model="autoScroll" />
              </div>
            </div>
          </div>
          <div class="input-container">
            <el-input
              v-model="userInput"
              type="textarea"
              :rows="3"
              placeholder="请输入您的问题..."
              :disabled="isStreaming"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <el-button 
              type="primary" 
              @click="sendMessage" 
              :loading="isStreaming"
              circle
              class="send-button"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
      </el-main>
    </el-container>
  </BaseLayout>
</template>

<script lang="ts">
import { ref, defineComponent, onMounted, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  ArrowLeft, 
  Plus, 
  Delete, 
  Star, 
  StarFilled,
  Loading,
  Promotion,
  Document,
  Download
} from '@element-plus/icons-vue'
import BaseLayout from '@/layouts/BaseLayout.vue'
import AgentService from '@/services/agent'
import ChatService, { type ChatSession, type ChatMessage } from '@/services/chat'
import GraphService, { type GraphRequest, type GraphNodeResponse } from '@/services/graph'
import PresetQuestionService from '@/services/presetQuestion'
import { type Agent } from '@/services/agent'
import HumanFeedback from '@/components/run/HumanFeedback.vue'
import ChatSessionSidebar from '@/components/run/ChatSessionSidebar.vue'

export default defineComponent({
  name: 'AgentRun',
  components: {
    BaseLayout,
    ArrowLeft,
    Plus,
    Delete,
    Star,
    StarFilled,
    Loading,
    Promotion,
    Document,
    Download,
    HumanFeedback,
    ChatSessionSidebar
  },
  setup() {
    const route = useRoute()

    // 响应式数据
    const agent = ref<Agent>({} as Agent)
    const currentSession = ref<ChatSession | null>(null)
    const currentMessages = ref<ChatMessage[]>([])
    const userInput = ref('')
    const isStreaming = ref(false)
    const streamingNodes = ref<GraphNodeResponse[]>([])
    const activeNodeTab = ref('')
    const requestOptions = ref({
      humanFeedback: false,
      nl2sqlOnly: false,
      plainReport: false
    })

    // 监听NL2SQL开关变化
    const handleNl2sqlOnlyChange = (value: boolean) => {
      if (value) {
        // 当仅NL2SQL开启时，禁用人工反馈和简洁报告，并设为false
        requestOptions.value.humanFeedback = false
        requestOptions.value.plainReport = false
      }
    }
    const autoScroll = ref(true)
    const chatContainer = ref<HTMLElement | null>(null)
    const nodeBlocks = ref<GraphNodeResponse[]>([])
    
    // 报告相关数据
    const htmlReportContent = ref('')
    const htmlReportSize = ref(0)
    const isHtmlReportComplete = ref(false)
    const markdownReportContent = ref('')
    
    // 人工反馈相关数据
    const showHumanFeedback = ref(false)
    const lastRequest = ref<GraphRequest | null>(null)

    const agentId = computed(() => route.params.id as string)

    const loadAgent = async () => {
      try {
        const agentData = await AgentService.get(parseInt(agentId.value))
        if (agentData) {
          agent.value = agentData
        } else {
          throw new Error('Agent 不存在')
        }
      } catch (error) {
        ElMessage.error('加载Agent失败')
        console.error('加载Agent失败:', error)
      }
    }

    const selectSession = async (session: ChatSession) => {
      currentSession.value = session
      streamingNodes.value = []
      nodeBlocks.value = []
      isStreaming.value = false
      try {
        currentMessages.value = await ChatService.getSessionMessages(session.id)
        scrollToBottom()
      } catch (error) {
        ElMessage.error('加载消息失败')
        console.error('加载消息失败:', error)
      }
    }

    const sendMessage = async () => {
      if(!userInput.value.trim()) {
        ElMessage.warning("请输入请求消息！")
        return
      }
      if (!currentSession.value || isStreaming.value) {
        ElMessage.warning("智能体正在处理中，请稍后...")
        return
      }

      const userMessage: ChatMessage = {
        sessionId: currentSession.value.id,
        role: 'user',
        content: userInput.value,
        messageType: 'text'
      }
      try {
        // 保存用户消息
        const savedMessage = await ChatService.saveMessage(currentSession.value.id, userMessage)
        currentMessages.value.push(savedMessage)
        
        const request: GraphRequest = {
          agentId: agentId.value,
          query: userInput.value,
          humanFeedback: requestOptions.value.humanFeedback,
          nl2sqlOnly: requestOptions.value.nl2sqlOnly,
          plainReport: requestOptions.value.plainReport,
          rejectedPlan: false,
          humanFeedbackContent: null
        }

        userInput.value = ''

        await sendGraphRequest(request, true)
      } catch(error) {
        ElMessage.error("未知错误")
        console.error(error)
      }
    }

    const sendGraphRequest = async (request: GraphRequest, rejectedPlan: boolean) => {
      try {
        lastRequest.value = request
        // 准备流式请求
        isStreaming.value = true
        nodeBlocks.value = []

        let currentNodeName: string | null = null

        // 重置报告状态
        resetReportState()

        const saveNodeMessage = async () => {
          if(nodeBlocks.value.length != 0) {
            // 保存上一个节点的数据
            const idx: number = nodeBlocks.value.length - 1;
            if(idx < 0) {
              ElMessage.error("未知错误");
              return;
            }
            const node: GraphNodeResponse = nodeBlocks.value[idx];
            // 使用generateNodeHtml方法生成HTML代码，确保显示与保存一致
            const nodeHtml = generateNodeHtml(node)
            
            const aiMessage: ChatMessage = {
              sessionId: currentSession.value!.id,
              role: 'assistant',
              content: nodeHtml,
              messageType: 'html'
            }
            
            await ChatService.saveMessage(currentSession.value!.id, aiMessage)
              .catch((error: any) => {
                console.error('保存AI消息失败:', error)
              })
          }
        }

        // 发送流式请求
        const closeStream = await GraphService.streamSearch(
          request,
          async (response: GraphNodeResponse) => {
            if (response.error) {
              ElMessage.error(`处理错误: ${response.text}`)
              return
            }

            lastRequest.value.threadId = response.threadId

            // 检查是否是报告节点
            if (response.nodeName === 'ReportGeneratorNode') {
              // 处理HTML报告
              if (response.textType === 'HTML') {
                htmlReportContent.value += response.text
                htmlReportSize.value = htmlReportContent.value.length
                
                // 更新显示：当前已经收集了多少字节的报告
                const reportNode = nodeBlocks.value.find((block: GraphNodeResponse) => block.nodeName === 'ReportGeneratorNode' && block.textType === 'HTML')
                if (reportNode) {
                  reportNode.text = `正在收集HTML报告... 已收集 ${htmlReportSize.value} 字节`
                } else {
                  nodeBlocks.value.push({
                    ...response,
                    text: `正在收集HTML报告... 已收集 ${htmlReportSize.value} 字节`
                  })
                }
              }
              // 处理Markdown报告
              else if (response.textType === 'MARK_DOWN') {
                markdownReportContent.value += response.text
                const markdownHtml = markdownToHtml(markdownReportContent.value)
                
                // 实时渲染Markdown报告
                const reportNode = nodeBlocks.value.find((block: GraphNodeResponse) => block.nodeName === 'ReportGeneratorNode' && block.textType === 'MARK_DOWN')
                if (reportNode) {
                  reportNode.text = markdownHtml
                } else {
                  nodeBlocks.value.push({
                    ...response,
                    text: markdownHtml
                  })
                }
              }
            } else {
              // 处理其他节点（原有逻辑）
              const newNode: boolean = response.nodeName !== currentNodeName;
              currentNodeName = response.nodeName;

              if(newNode) {
                await saveNodeMessage()
                // 创建新的节点块
                const newBlock: GraphNodeResponse = {
                  ...response,
                  text: response.text
                }
                nodeBlocks.value.push(newBlock)
              } else {
                const idx: number = nodeBlocks.value.length - 1;
                if(idx < 0) {
                  ElMessage.error("未知错误");
                  return;
                }
                nodeBlocks.value[idx].text += response.text;
              }
            }

            if(autoScroll.value) {
              scrollToBottom()
            }
          },
          async (error: Error) => {
            ElMessage.error(`流式请求失败: ${error.message}`)
            console.error("error: " + error)
            isStreaming.value = false
            currentNodeName = null
          },
          async () => {            
            // 保存报告到后端
            if (htmlReportContent.value) {
              const htmlReportMessage: ChatMessage = {
                sessionId: currentSession.value!.id,
                role: 'assistant',
                content: htmlReportContent.value,
                messageType: 'html-report'
              }
              
              await ChatService.saveMessage(currentSession.value!.id, htmlReportMessage)
                .catch((error: any) => {
                  ElMessage.error('保存HTML报告失败！')
                  console.error('保存HTML报告失败:', error)
                })
            } else if (markdownReportContent.value) {
              const markdownHtml = markdownToHtml(markdownReportContent.value)
              const markdownMessage: ChatMessage = {
                sessionId: currentSession.value!.id,
                role: 'assistant',
                content: markdownHtml,
                messageType: 'html'
              }
              
              await ChatService.saveMessage(currentSession.value!.id, markdownMessage)
                .catch((error: any) => {
                  console.error('保存Markdown报告失败:', error)
                })
            } else {
              // 其他节点，可能是错误或人类反馈模式
              await saveNodeMessage()
              
              // 如果是人工反馈模式，显示反馈组件
              if (requestOptions.value.humanFeedback && rejectedPlan) {
                showHumanFeedback.value = true
              } else {
                // 所有节点处理完成
                isStreaming.value = false
              }
            }
            
            ElMessage.success('处理完成')
            currentNodeName = null
            closeStream()
            await selectSession(currentSession.value)
          }
        )

      } catch (error) {
        ElMessage.error('发送消息失败')
        console.error('发送消息失败:', error)
        isStreaming.value = false
      }
    }

    const formatMessageContent = (message: ChatMessage) => {
      if (message.messageType === 'text') {
        return message.content.replace(/\n/g, '<br>')
      }
      return message.content
    }

    // 从消息内容下载HTML报告
    const downloadHtmlReportFromMessage = (content: string) => {
      if (!content) {
        ElMessage.warning('没有可下载的HTML报告')
        return
      }

      const blob = new Blob([content], { type: 'text/html' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `report_${new Date().getTime()}.html`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      ElMessage.success('HTML报告下载成功')
    }

    // 生成节点容器的HTML代码
    const generateNodeHtml = (node: GraphNodeResponse) => {
      let content = formatNodeContent(node)

      return `
        <div class="agent-response-block" style="display: block !important; width: 100% !important;">
          <div class="agent-response-title">${node.nodeName}</div>
          <div class="agent-response-content">${content}</div>
        </div>
      `
    }

    const formatNodeContent = (node: GraphNodeResponse) => {
      let content = node.text
      return content
    }


    // Markdown转HTML
    // todo: 使用其他高亮库
    const markdownToHtml = (markdown: string): string => {
      // 简单的Markdown转HTML实现
      let html = markdown
        // 标题
        .replace(/^# (.*$)/gim, '<h1>$1</h1>')
        .replace(/^## (.*$)/gim, '<h2>$1</h2>')
        .replace(/^### (.*$)/gim, '<h3>$1</h3>')
        // 粗体
        .replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
        // 斜体
        .replace(/\*(.*?)\*/gim, '<em>$1</em>')
        // 代码块
        .replace(/```([^`]+)```/gim, '<pre><code>$1</code></pre>')
        // 行内代码
        .replace(/`([^`]+)`/gim, '<code>$1</code>')
        // 链接
        .replace(/\[([^\]]+)\]\(([^)]+)\)/gim, '<a href="$2">$1</a>')
        // 图片
        .replace(/!\[([^\]]+)\]\(([^)]+)\)/gim, '<img src="$2" alt="$1">')
        // 换行
        .replace(/\n/g, '<br>')
      
      return html
    }

    // 重置报告状态
    const resetReportState = () => {
      htmlReportContent.value = ''
      htmlReportSize.value = 0
      isHtmlReportComplete.value = false
      markdownReportContent.value = ''
    }

    // 快速操作按钮 - 从预设问题获取
    const quickActions = ref<Array<{id: number, label: string, message: string, icon: string}>>([])

    // 加载预设问题
    const loadPresetQuestions = async () => {
      try {
        const questions = await PresetQuestionService.list(parseInt(agentId.value))
        quickActions.value = questions.map((q: any, index: number) => ({
          id: index + 1,
          label: q.title,
          message: q.content,
          icon: 'Promotion'
        }))
      } catch (error) {
        console.error('加载预设问题失败:', error)
        // 如果加载失败，使用默认问题
        quickActions.value = [
          {
            id: 1,
            label: '查询最近数据',
            message: '帮我查询最近一个月的数据',
            icon: 'Promotion'
          },
          {
            id: 2,
            label: '生成销售报告',
            message: '生成一份销售分析报告',
            icon: 'Promotion'
          }
        ]
      }
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (chatContainer.value) {
          chatContainer.value.scrollTop = chatContainer.value.scrollHeight
        }
      })
    }

    const handleHumanFeedback = async (request: GraphRequest, rejectedPlan: boolean, content: string) => {
      content = content.trim() || 'Accept'
      showHumanFeedback.value = false
      const newRequest: GraphRequest = { ...request }
      newRequest.rejectedPlan = rejectedPlan
      newRequest.humanFeedbackContent = content
      await sendGraphRequest(newRequest, rejectedPlan)
    }

    // 生命周期
    onMounted(async () => {
      await loadAgent()
      await loadPresetQuestions()
    })

    return {
      agent,
      currentSession,
      currentMessages,
      userInput,
      isStreaming,
      streamingNodes,
      activeNodeTab,
      requestOptions,
      autoScroll,
      chatContainer,
      nodeBlocks,
      agentId,
      showHumanFeedback,
      lastRequest,
      selectSession,
      sendMessage,
      formatMessageContent,
      formatNodeContent,
      generateNodeHtml,
      handleNl2sqlOnlyChange,
      downloadHtmlReportFromMessage,
      markdownToHtml,
      resetReportState,
      handleHumanFeedback
    }
  }
})
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
  align-items: center;
  justify-content: center;
  height: 200px;
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
