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
              <div v-if="message.messageType === 'html'" class="message-rich-card">
                <div v-html="message.content"></div>
              </div>
              <!-- 数据集消息尝试图表渲染 -->
              <div v-else-if="message.messageType === 'result-set'" class="result-set-message">
                <ResultSetDisplay
                  v-if="message.content"
                  :resultData="JSON.parse(message.content)"
                  :pageSize="resultSetPageSize"
                />
              </div>
              <div
                v-else-if="message.messageType === 'markdown-report'"
                class="markdown-report-message"
              >
                <div
                  class="markdown-report-header"
                  style="display: flex; justify-content: space-between; align-items: center"
                >
                  <div class="report-info">
                    <el-icon><Document /></el-icon>
                    <span>报告已生成</span>
                    <el-radio-group
                      v-model="requestOptions.reportFormat"
                      size="small"
                      class="report-format-inline"
                    >
                      <el-radio-button value="markdown">Markdown</el-radio-button>
                      <el-radio-button value="html">HTML</el-radio-button>
                    </el-radio-group>
                  </div>
                  <el-button-group size="large">
                    <el-button
                      type="primary"
                      @click="downloadMarkdownReportFromMessage(`${message.content}`)"
                    >
                      <el-icon><Download /></el-icon>
                      下载Markdown报告
                    </el-button>
                    <el-button
                      type="success"
                      @click="downloadHtmlReportFromMessageByServer(`${message.content}`)"
                    >
                      <el-icon><Download /></el-icon>
                      下载HTML报告
                    </el-button>
                    <el-tooltip content="全屏查看报告" placement="top">
                      <el-button type="info" @click="openReportFullscreen(message.content)">
                        <el-icon><FullScreen /></el-icon>
                        全屏
                      </el-button>
                    </el-tooltip>
                  </el-button-group>
                </div>
                <div class="markdown-report-content">
                  <markdown-agent-container
                    v-if="requestOptions.reportFormat === 'markdown'"
                    class="md-body"
                    :content="message.content"
                    :options="options"
                  />
                  <ReportHtmlView v-else :content="message.content" />
                </div>
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
                <template v-for="(nodeBlock, index) in nodeBlocks" :key="index">
                  <!-- 如果是 Markdown 报告节点，使用 Markdown 或 HTML 组件 -->
                  <div
                    v-if="
                      nodeBlock.length > 0 &&
                      nodeBlock[0].nodeName === 'ReportGeneratorNode' &&
                      nodeBlock[0].textType === 'MARK_DOWN'
                    "
                    class="agent-response-block"
                  >
                    <div class="agent-response-title">
                      {{ nodeBlock[0].nodeName }}
                    </div>
                    <div class="agent-response-content">
                      <markdown-agent-container
                        v-if="requestOptions.reportFormat === 'markdown'"
                        class="md-body"
                        :content="getMarkdownContentFromNode(nodeBlock)"
                        :options="options"
                      />
                      <ReportHtmlView v-else :content="getMarkdownContentFromNode(nodeBlock)" />
                    </div>
                  </div>
                  <!-- 如果是 RESULT_SET 节点，使用 ResultSetDisplay 组件 -->
                  <div
                    v-else-if="nodeBlock.length > 0 && nodeBlock[0].textType === 'RESULT_SET'"
                    class="agent-response-block"
                  >
                    <div class="agent-response-title">
                      {{ nodeBlock[0].nodeName }}
                    </div>
                    <div class="agent-response-content">
                      <ResultSetDisplay
                        v-if="nodeBlock[0].text"
                        :resultData="JSON.parse(nodeBlock[0].text)"
                        :pageSize="resultSetPageSize"
                      />
                    </div>
                  </div>
                  <!-- 其他节点使用原来的 HTML 渲染方式 -->
                  <div v-else v-html="generateNodeHtml(nodeBlock)"></div>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area" v-if="currentSession">
          <div class="input-controls">
            <div
              class="input-controls-header"
              @click="inputControlsCollapsed = !inputControlsCollapsed"
            >
              <span class="input-controls-title">更多选项</span>
              <el-button
                type="primary"
                size="small"
                class="input-controls-toggle-btn"
                :class="{ collapsed: inputControlsCollapsed }"
              >
                <el-icon class="input-controls-toggle-icon">
                  <ArrowDown />
                </el-icon>
                {{ inputControlsCollapsed ? '展开' : '收起' }}
              </el-button>
            </div>
            <div v-show="!inputControlsCollapsed" class="input-controls-body">
              <!-- 预设问题区域 -->
              <PresetQuestions
                v-if="currentSession && agent.id"
                :agentId="agent.id"
                :onQuestionClick="handlePresetQuestionClick"
              />
              <div class="switch-group">
                <div class="switch-item">
                  <span class="switch-label">每页数量</span>
                  <el-select
                    v-model="resultSetPageSize"
                    :disabled="isStreaming"
                    style="width: 80px"
                  >
                    <el-option label="5" :value="5" />
                    <el-option label="10" :value="10" />
                    <el-option label="20" :value="20" />
                    <el-option label="50" :value="50" />
                    <el-option label="100" :value="100" />
                  </el-select>
                </div>
                <div class="switch-item switch-item-action">
                  <el-button
                    type="primary"
                    plain
                    :disabled="!latestExplainRuntimeRequestId"
                    @click="openLatestAnswerExplain"
                  >
                    查看数据来源
                  </el-button>
                </div>
                <!-- <div class="switch-item">
                <span class="switch-label">报告格式</span>
                <el-radio-group v-model="requestOptions.reportFormat" size="small">
                  <el-radio-button value="markdown">Markdown</el-radio-button>
                  <el-radio-button value="html">HTML</el-radio-button>
                </el-radio-group>
              </div> -->
              </div>
            </div>
          </div>
          <div v-if="pendingClarify" class="clarify-banner">
            <div class="clarify-banner-header">
              <span class="clarify-banner-title">需要先澄清后再查库</span>
              <span class="clarify-banner-risk">riskLevel={{ pendingClarify.riskLevel }}</span>
            </div>
            <div class="clarify-banner-body">
              {{ pendingClarify.summary || '当前问题存在高歧义，下一条输入将作为补充信息或显式假设回填。' }}
            </div>
            <div v-if="pendingClarify.missingDimensions.length > 0" class="clarify-banner-tags">
              <el-tag
                v-for="dimension in pendingClarify.missingDimensions"
                :key="dimension"
                size="small"
                effect="plain"
              >
                {{ dimension }}
              </el-tag>
            </div>
            <div
              v-if="pendingClarify.suggestedAssumptions.length > 0"
              class="clarify-banner-assumptions"
            >
              <el-button
                v-for="assumption in pendingClarify.suggestedAssumptions"
                :key="assumption"
                size="small"
                text
                bg
                :disabled="isStreaming || isSubmittingMessage"
                @click="applyClarifyAssumption(assumption)"
              >
                {{ assumption }}
              </el-button>
            </div>
            <div class="clarify-banner-footer">
              <span>下一条输入会作为人工反馈补充给上一个高歧义问题，填好后请点击发送按钮提交。</span>
              <el-button link type="primary" @click="cancelPendingClarify">改为新问题</el-button>
            </div>
          </div>
          <div class="input-container">
            <el-button
              text
              bg
              size="small"
              class="trace-button"
              :disabled="traceLoading"
              @click="openTraceDialog"
            >
              Trace
            </el-button>
            <el-input
              v-model="userInput"
              type="textarea"
              :rows="3"
              :placeholder="
                pendingClarify
                  ? '请输入补充信息，或直接写“按以下假设继续：...”'
                  : '请输入您的问题...'
              "
              :disabled="isStreaming || isSubmittingMessage"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <el-button
              v-if="!isStreaming"
              type="primary"
              :disabled="isSubmittingMessage"
              @click="sendMessage"
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

    <!-- 报告全屏遮罩 -->
    <Teleport to="body">
      <div
        v-if="showReportFullscreen"
        class="report-fullscreen-overlay"
        @click.self="closeReportFullscreen"
      >
        <div class="report-fullscreen-container">
          <div class="report-fullscreen-header">
            <span class="report-fullscreen-title">
              {{ requestOptions.reportFormat === 'markdown' ? 'Markdown 报告' : 'HTML 报告' }}
            </span>
            <el-button
              type="danger"
              circle
              class="report-fullscreen-close"
              @click="closeReportFullscreen"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div class="report-fullscreen-content">
            <markdown-agent-container
              v-if="requestOptions.reportFormat === 'markdown'"
              class="md-body report-fullscreen-body"
              :content="fullscreenReportContent"
              :options="options"
            />
            <ReportHtmlView
              v-else
              :content="fullscreenReportContent"
              class="report-fullscreen-body"
            />
          </div>
        </div>
      </div>
    </Teleport>

    <el-dialog
      v-model="traceDialogVisible"
      title="最近一次 Trace"
      width="1280px"
      top="4vh"
      destroy-on-close
    >
      <div class="trace-toolbar">
        <div v-if="sessionTrace" class="trace-summary">
          <span class="trace-summary-pill">Trace ID: {{ sessionTrace.traceId }}</span>
          <span class="trace-summary-pill">Span 数: {{ sessionTrace.spanCount }}</span>
          <span class="trace-summary-pill">
            耗时: {{ formatTraceDuration(sessionTrace.durationMs) }}
          </span>
          <span class="trace-summary-pill">
            开始时间: {{ formatTraceTime(sessionTrace.startEpochMs) }}
          </span>
          <span v-if="sessionTrace.runtimeRequestId" class="trace-summary-pill">
            Request: {{ sessionTrace.runtimeRequestId }}
          </span>
          <span v-if="sessionTrace.agentId" class="trace-summary-pill">
            Agent: {{ sessionTrace.agentId }}
          </span>
        </div>
        <div class="trace-toolbar-actions">
          <el-input
            v-model="traceSearchKeyword"
            clearable
            placeholder="搜索 span / 属性 / 值"
            class="trace-search-input"
          />
          <el-button size="small" :loading="traceLoading" @click="refreshTrace">刷新</el-button>
        </div>
      </div>

      <el-alert
        v-if="traceError"
        :title="traceError"
        type="info"
        :closable="false"
        show-icon
        class="trace-alert"
      />
      <el-empty
        v-else-if="!traceLoading && !sessionTrace"
        description="当前会话还没有最近一次 trace"
      />

      <div v-if="sessionTrace" class="trace-explorer">
        <div class="trace-pane trace-pane-list">
          <div class="trace-pane-header">
            <span class="trace-pane-title">Span 列表</span>
            <span class="trace-pane-count">
              {{ filteredTraceSpans.length }}/{{ flattenedTraceSpans.length }}
            </span>
          </div>
          <el-empty
            v-if="filteredTraceSpans.length === 0"
            description="没有匹配的 span，试试其他关键词"
            :image-size="88"
          />
          <div v-else class="trace-list">
            <button
              v-for="row in filteredTraceSpans"
              :key="row.span.spanId"
              type="button"
              class="trace-row"
              :class="{
                'is-selected': row.span.spanId === selectedTraceSpanId,
                'is-error': row.span.status === 'ERROR',
              }"
              :style="{ paddingLeft: `${row.depth * 32 + 20}px` }"
              @click="selectTraceSpan(row.span.spanId)"
            >
              <div class="trace-row-main">
                <span class="trace-row-name">{{ row.span.name }}</span>
                <el-tag size="small" effect="plain">{{ row.span.kind }}</el-tag>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="row.span.status === 'ERROR' ? 'danger' : 'success'"
                >
                  {{ row.span.status }}
                </el-tag>
                <span class="trace-row-duration">
                  {{ formatTraceDuration(row.span.durationMs) }}
                </span>
              </div>
              <div class="trace-row-meta">
                <span>spanId: {{ row.span.spanId }}</span>
                <span>parent: {{ row.span.parentSpanId || '-' }}</span>
                <span>属性: {{ row.attributeEntries.length }}</span>
                <span>偏移: {{ formatTraceOffset(row.span.startEpochMs) }}</span>
              </div>
            </button>
          </div>
        </div>

        <div class="trace-pane trace-pane-detail">
          <template v-if="selectedTraceRow">
            <div class="trace-detail-header">
              <div>
                <div class="trace-detail-title">{{ selectedTraceRow.span.name }}</div>
                <div class="trace-detail-subtitle">
                  <span>spanId: {{ selectedTraceRow.span.spanId }}</span>
                  <span>parent: {{ selectedTraceRow.span.parentSpanId || '-' }}</span>
                </div>
              </div>
              <div class="trace-detail-tags">
                <el-tag effect="plain">{{ selectedTraceRow.span.kind }}</el-tag>
                <el-tag
                  effect="plain"
                  :type="selectedTraceRow.span.status === 'ERROR' ? 'danger' : 'success'"
                >
                  {{ selectedTraceRow.span.status }}
                </el-tag>
              </div>
            </div>

            <el-descriptions :column="2" border size="small" class="trace-descriptions">
              <el-descriptions-item label="开始时间">
                {{ formatTraceTime(selectedTraceRow.span.startEpochMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="结束时间">
                {{ formatTraceTime(selectedTraceRow.span.endEpochMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="耗时">
                {{ formatTraceDuration(selectedTraceRow.span.durationMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="相对偏移">
                {{ formatTraceOffset(selectedTraceRow.span.startEpochMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="属性数">
                {{ selectedTraceRow.attributeEntries.length }}
              </el-descriptions-item>
              <el-descriptions-item label="子 Span 数">
                {{ selectedTraceRow.span.children?.length ?? 0 }}
              </el-descriptions-item>
            </el-descriptions>

            <div v-if="parsedTraceConversations.length > 0" class="trace-message-panel">
              <div class="trace-pane-header">
                <span class="trace-pane-title">消息视图</span>
                <span class="trace-pane-count">{{ parsedTraceConversations.length }} 组</span>
              </div>
              <div class="trace-message-groups">
                <div
                  v-for="group in parsedTraceConversations"
                  :key="group.attributeKey"
                  class="trace-message-group"
                >
                  <div class="trace-message-group-header">
                    <div class="trace-message-group-title">{{ group.title }}</div>
                    <div class="trace-message-group-meta">{{ group.attributeKey }}</div>
                  </div>
                  <div class="trace-message-list">
                    <div
                      v-for="message in group.messages"
                      :key="message.id"
                      class="trace-message-item"
                      :class="`is-${message.kind}`"
                    >
                      <div class="trace-message-role">
                        <span class="trace-message-role-badge">{{ message.label }}</span>
                      </div>
                      <div class="trace-message-body">
                        <div v-if="message.title" class="trace-message-title">
                          {{ message.title }}
                        </div>
                        <div v-if="message.skills.length > 0" class="trace-message-skills">
                          <span
                            v-for="skill in message.skills"
                            :key="`${message.id}-${skill}`"
                            class="trace-skill-chip"
                          >
                            {{ skill }}
                          </span>
                        </div>
                        <pre
                          v-if="isStructuredTraceValue(message.content)"
                          class="trace-message-content trace-message-content-structured"
                          >{{ formatStructuredTraceValue(message.content) }}</pre
                        >
                        <div v-else class="trace-message-content">
                          {{ message.content || '空内容' }}
                        </div>
                        <pre v-if="message.details" class="trace-message-details">{{
                          message.details
                        }}</pre>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="trace-attribute-panel">
              <div class="trace-pane-header">
                <span class="trace-pane-title">属性详情</span>
                <span class="trace-pane-count">
                  {{ selectedTraceAttributeEntries.length }}/{{
                    selectedTraceRow.attributeEntries.length
                  }}
                </span>
              </div>

              <el-empty
                v-if="selectedTraceAttributeEntries.length === 0"
                description="这个 span 没有可显示的属性"
                :image-size="88"
              />
              <div v-else class="trace-attribute-table">
                <div class="trace-attribute-table-header">
                  <span>Key</span>
                  <span>Value</span>
                </div>
                <div
                  v-for="entry in selectedTraceAttributeEntries"
                  :key="`${selectedTraceRow.span.spanId}-${entry.key}`"
                  class="trace-attribute-row"
                >
                  <div class="trace-attribute-key">{{ entry.key }}</div>
                  <pre
                    v-if="isStructuredTraceValue(entry.value)"
                    class="trace-attribute-value trace-attribute-value-structured"
                    >{{ formatStructuredTraceValue(entry.value) }}</pre
                  >
                  <div v-else class="trace-attribute-value">{{ entry.value }}</div>
                </div>
              </div>
            </div>
          </template>
          <el-empty v-else description="选择一个 span 查看详情" :image-size="88" />
        </div>
      </div>
    </el-dialog>

    <el-drawer v-model="answerExplainVisible" title="数据来源与解释链" size="48%" destroy-on-close>
      <el-skeleton v-if="answerExplainLoading" animated :rows="10" />
      <el-alert
        v-else-if="answerExplainError"
        :title="answerExplainError"
        type="warning"
        :closable="false"
        show-icon
      />
      <div v-else-if="answerExplain" class="answer-explain-panel">
        <div class="answer-explain-summary">
          <span class="answer-explain-pill">Request: {{ answerExplain.runtimeRequestId }}</span>
          <span v-if="answerExplain.datasource" class="answer-explain-pill">
            数据源: {{ answerExplain.datasource }}
          </span>
          <span v-if="answerExplain.updatedAt" class="answer-explain-pill">
            更新时间: {{ formatTraceTime(answerExplain.updatedAt) }}
          </span>
        </div>

        <section v-if="answerExplain.semanticHits.length > 0" class="answer-explain-section">
          <div class="answer-explain-section-title">问题理解</div>
          <div class="answer-explain-card-list">
            <article
              v-for="(hit, index) in answerExplain.semanticHits"
              :key="`semantic-${index}`"
              class="answer-explain-card"
            >
              <div class="answer-explain-card-title">
                {{ hit.tableName || '未命名表' }}
                <template v-if="hit.columnName">.{{ hit.columnName }}</template>
              </div>
              <div class="answer-explain-card-meta">
                <span>分数: {{ hit.score ?? '-' }}</span>
                <span>命中依据: {{ hit.matchedBy || '-' }}</span>
              </div>
              <div v-if="hit.businessName" class="answer-explain-card-body">
                业务名: {{ hit.businessName }}
              </div>
              <div
                v-if="hit.businessDescription || hit.relationHint"
                class="answer-explain-card-body"
              >
                {{ hit.businessDescription || hit.relationHint }}
              </div>
            </article>
          </div>
        </section>

        <section
          v-if="answerExplain.sql || answerExplain.sqlExplanation"
          class="answer-explain-section"
        >
          <div class="answer-explain-section-title">SQL 解释</div>
          <div v-if="answerExplain.sqlExplanation" class="answer-explain-sql-summary">
            {{ answerExplain.sqlExplanation }}
          </div>
          <pre v-if="answerExplain.sql" class="answer-explain-code">{{ answerExplain.sql }}</pre>
        </section>

        <section class="answer-explain-section">
          <div class="answer-explain-section-title">数据来源</div>
          <div class="answer-explain-kv">
            <div class="answer-explain-kv-row">
              <span class="answer-explain-kv-key">执行说明</span>
              <span class="answer-explain-kv-value">
                {{ summarizeExplainExecution(answerExplain) }}
              </span>
            </div>
            <div v-if="answerExplain.question" class="answer-explain-kv-row">
              <span class="answer-explain-kv-key">用户问题</span>
              <span class="answer-explain-kv-value">{{ answerExplain.question }}</span>
            </div>
            <div v-if="answerExplain.answer" class="answer-explain-kv-row">
              <span class="answer-explain-kv-key">最终回答</span>
              <span class="answer-explain-kv-value">{{ answerExplain.answer }}</span>
            </div>
            <div v-if="answerExplain.datasource" class="answer-explain-kv-row">
              <span class="answer-explain-kv-key">数据源</span>
              <span class="answer-explain-kv-value">{{ answerExplain.datasource }}</span>
            </div>
            <div v-if="answerExplain.usedTables.length > 0" class="answer-explain-kv-row">
              <span class="answer-explain-kv-key">使用表</span>
              <span class="answer-explain-kv-value">{{ answerExplain.usedTables.join('、') }}</span>
            </div>
            <div v-if="answerExplain.usedColumns.length > 0" class="answer-explain-kv-row">
              <span class="answer-explain-kv-key">使用字段</span>
              <span class="answer-explain-kv-value">
                {{ answerExplain.usedColumns.join('、') }}
              </span>
            </div>
            <div
              v-for="([key, value], index) in Object.entries(answerExplain.permissions || {})"
              :key="`permission-${index}`"
              class="answer-explain-kv-row"
            >
              <span class="answer-explain-kv-key">{{ key }}</span>
              <span class="answer-explain-kv-value">{{ formatExplainValue(value) }}</span>
            </div>
            <div
              v-for="([key, value], index) in Object.entries(answerExplain.stats || {})"
              :key="`stat-${index}`"
              class="answer-explain-kv-row"
            >
              <span class="answer-explain-kv-key">{{ key }}</span>
              <span class="answer-explain-kv-value">{{ formatExplainValue(value) }}</span>
            </div>
          </div>
        </section>

        <section v-if="answerExplain.toolSteps.length > 0" class="answer-explain-section">
          <div class="answer-explain-section-title">执行过程</div>
          <div class="answer-explain-step-list">
            <article
              v-for="(step, index) in answerExplain.toolSteps"
              :key="`step-${index}`"
              class="answer-explain-step"
            >
              <div class="answer-explain-step-title">
                {{ step.title || step.toolName || '未命名步骤' }}
              </div>
              <div class="answer-explain-step-meta">
                <span>{{ step.toolName || '-' }}</span>
                <span v-if="step.datasource">数据源: {{ step.datasource }}</span>
                <span v-if="step.timestampEpochMs">
                  {{ formatTraceTime(step.timestampEpochMs) }}
                </span>
              </div>
              <div v-if="step.summary" class="answer-explain-step-body">{{ step.summary }}</div>
              <pre v-if="step.detail" class="answer-explain-step-detail">{{ step.detail }}</pre>
            </article>
          </div>
        </section>

        <section v-if="answerExplain.knowledgeHits.length > 0" class="answer-explain-section">
          <div class="answer-explain-section-title">知识来源</div>
          <div class="answer-explain-card-list">
            <article
              v-for="(hit, index) in answerExplain.knowledgeHits"
              :key="`knowledge-${index}`"
              class="answer-explain-card"
            >
              <div class="answer-explain-card-title">
                {{ hit.title || hit.source || '知识命中' }}
              </div>
              <div class="answer-explain-card-meta">
                <span>{{ hit.vectorType || '-' }}</span>
                <span v-if="hit.concreteType">{{ hit.concreteType }}</span>
                <span v-if="hit.source">{{ hit.source }}</span>
              </div>
              <div v-if="hit.summary" class="answer-explain-card-body">{{ hit.summary }}</div>
              <div v-if="hit.snippet" class="answer-explain-card-body">{{ hit.snippet }}</div>
            </article>
          </div>
        </section>

        <section v-if="answerExplain.warnings.length > 0" class="answer-explain-section">
          <div class="answer-explain-section-title">提示与限制</div>
          <ul class="answer-explain-warning-list">
            <li v-for="(warning, index) in answerExplain.warnings" :key="`warning-${index}`">
              {{ warning }}
            </li>
          </ul>
        </section>
      </div>
      <el-empty v-else description="当前回答还没有可展示的数据来源信息" :image-size="96" />
    </el-drawer>
  </BaseLayout>
</template>

<script lang="ts">
  import { ref, defineComponent, onMounted, nextTick, computed } from 'vue';
  import { useRoute } from 'vue-router';
  import { ElMessage } from 'element-plus';
  import {
    Loading,
    Promotion,
    Document,
    Download,
    CircleClose,
    FullScreen,
    Close,
    ArrowDown,
  } from '@element-plus/icons-vue';
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
  import ChatService, {
    type AnswerTraceExplain,
    type ChatSession,
    type ChatMessage,
    type SessionTrace,
    type TraceSpan,
  } from '@/services/chat';
  import GraphService, {
    type ClarifyMetadata,
    type GraphRequest,
    type GraphNodeResponse,
    TextType,
  } from '@/services/graph';
  import { type Agent } from '@/services/agent';
  import { type ResultData, type ResultSetData } from '@/services/resultSet';
  import {
    type PendingClarifyState,
    SessionRuntimeState,
    useSessionStateManager,
  } from '@/services/sessionStateManager';
  import ChatSessionSidebar from '@/components/run/ChatSessionSidebar.vue';
  import PresetQuestions from '@/components/run/PresetQuestions.vue';
  import MarkdownAgentContainer from '@/components/run/markdown';
  import ReportHtmlView from '@/components/run/ReportHtmlView.vue';
  import ResultSetDisplay from '@/components/run/ResultSetDisplay.vue';

  // 扩展Window接口以包含自定义方法
  declare global {
    interface Window {
      copyTextToClipboard: (btn: HTMLElement) => void;
      handleResultSetPagination: (btn: HTMLElement, direction: 'prev' | 'next') => void;
    }
  }

  interface MessageExplainMetadata {
    runtimeRequestId?: string;
    explainAvailable?: boolean;
    nodeName?: string;
  }

  const isClarifyMetadata = (
    metadata?: (ClarifyMetadata & Record<string, any>) | null,
  ): metadata is ClarifyMetadata & { clarifyRequired: true; originalQuery: string } => {
    return Boolean(metadata?.clarifyRequired && metadata?.originalQuery);
  };

  export default defineComponent({
    name: 'AgentRun',
    components: {
      BaseLayout,
      Loading,
      Promotion,
      Document,
      Download,
      CircleClose,
      FullScreen,
      Close,
      ArrowDown,
      ChatSessionSidebar,
      PresetQuestions,
      MarkdownAgentContainer,
      ReportHtmlView,
      ResultSetDisplay,
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
      const isSubmittingMessage = ref(false);
      const nodeBlocks = ref<GraphNodeResponse[][]>([]);
      const options = ref({
        markdownIt: {
          linkify: true,
        },
        linkAttributes: {
          attrs: {
            target: '_blank',
            rel: 'noopener',
          },
        },
      });
      const requestOptions = ref({
        reportFormat: 'markdown' as 'markdown' | 'html', // 'markdown' | 'html'，控制报告展示方式
      });
      const showReportFullscreen = ref(false);
      const fullscreenReportContent = ref('');
      const inputControlsCollapsed = ref(false);
      const traceDialogVisible = ref(false);
      const traceLoading = ref(false);
      const traceError = ref('');
      const traceSearchKeyword = ref('');
      const selectedTraceSpanId = ref('');
      const sessionTrace = ref<SessionTrace | null>(null);
      const answerExplainVisible = ref(false);
      const answerExplainLoading = ref(false);
      const answerExplainError = ref('');
      const answerExplain = ref<AnswerTraceExplain | null>(null);
      const pendingClarify = ref<PendingClarifyState | null>(null);

      const chatContainer = ref<HTMLElement | null>(null);

      // 结果集显示配置
      const resultSetPageSize = ref(20);

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
          saveViewToState(currentSession.value.id, {
            isStreaming,
            nodeBlocks,
            answerExplain,
            answerExplainVisible,
            pendingClarify,
          });
        }
        currentSession.value = session;
        sessionTrace.value = null;
        traceError.value = '';
        traceSearchKeyword.value = '';
        selectedTraceSpanId.value = '';
        traceDialogVisible.value = false;

        try {
          if (session === null) {
            currentMessages.value = [];
            nodeBlocks.value = [];
            isStreaming.value = false;
            answerExplainVisible.value = false;
            answerExplain.value = null;
            answerExplainError.value = '';
            pendingClarify.value = null;
            return;
          }
          syncStateToView(session.id, {
            isStreaming,
            nodeBlocks,
            answerExplain,
            answerExplainVisible,
            pendingClarify,
          });
          currentMessages.value = await ChatService.getSessionMessages(session.id);
          scrollToBottom();
        } catch (error) {
          ElMessage.error('加载消息失败');
          console.error('加载消息失败:', error);
        }
      };

      const buildPendingClarifyState = (
        metadata: ClarifyMetadata & { originalQuery: string },
      ): PendingClarifyState => {
        return {
          originalQuery: metadata.originalQuery,
          riskLevel: metadata.riskLevel || 'high',
          summary: metadata.summary,
          missingDimensions: metadata.missingDimensions || [],
          followUpQuestions: metadata.followUpQuestions || [],
          suggestedAssumptions: metadata.suggestedAssumptions || [],
        };
      };

      const cancelPendingClarify = () => {
        pendingClarify.value = null;
        if (currentSession.value) {
          getSessionState(currentSession.value.id).pendingClarify = null;
        }
      };

      const applyClarifyAssumption = (assumption: string) => {
        userInput.value = `按以下假设继续：${assumption}`;
      };

      const sendMessage = async () => {
        if (!userInput.value.trim()) {
          ElMessage.warning('请输入请求消息！');
          return;
        }
        if (isSubmittingMessage.value) {
          return;
        }
        if (!currentSession.value || isStreaming.value) {
          ElMessage.warning('智能体正在处理中，请稍后...');
          return;
        }

        isSubmittingMessage.value = true;
        const needsTitle = !currentSession.value?.title || currentSession.value.title === '新会话';
        const activeClarify = pendingClarify.value;
        const requestQuery = activeClarify?.originalQuery ?? userInput.value.trim();
        const feedbackContent = activeClarify ? userInput.value.trim() : undefined;

        const userMessage: ChatMessage = {
          sessionId: currentSession.value.id,
          role: 'user',
          content: userInput.value,
          messageType: 'text',
          titleNeeded: needsTitle && !activeClarify,
        };
        try {
          // 保存用户消息
          const savedMessage = await ChatService.saveMessage(currentSession.value.id, userMessage);
          currentMessages.value.push(savedMessage);
          getSessionState(currentSession.value.id);

          const request: GraphRequest = {
            agentId: agentId.value,
            query: requestQuery,
            humanFeedback: Boolean(activeClarify),
            humanFeedbackContent: feedbackContent,
            nl2sqlOnly: false,
            rejectedPlan: false,
            threadId: currentSession.value.id,
            runtimeRequestId: createRuntimeRequestId(),
          };

          userInput.value = '';
          pendingClarify.value = null;
          getSessionState(currentSession.value.id).pendingClarify = null;

          await sendGraphRequest(request);
        } catch (error) {
          if (activeClarify && currentSession.value) {
            pendingClarify.value = activeClarify;
            getSessionState(currentSession.value.id).pendingClarify = activeClarify;
          }
          ElMessage.error('未知错误');
          console.error(error);
        }
        finally {
          isSubmittingMessage.value = false;
        }
      };

      const buildExplainMetadataForNode = (
        request: GraphRequest,
        node: GraphNodeResponse[],
      ): MessageExplainMetadata | null => {
        if (!request.runtimeRequestId || !node.length) {
          return null;
        }
        const firstNode = node[0];
        const explainAvailable =
          firstNode.nodeName === 'AgentScopeRuntime' || firstNode.textType === TextType.RESULT_SET;
        if (!explainAvailable) {
          return null;
        }
        return {
          runtimeRequestId: request.runtimeRequestId,
          explainAvailable: true,
          nodeName: firstNode.nodeName,
        };
      };

      const saveAssistantNodeMessage = async (
        sessionId: string,
        node: GraphNodeResponse[],
        request?: GraphRequest | null,
      ): Promise<void> => {
        if (!node || !node.length) {
          return;
        }
        const explainMetadata = request ? buildExplainMetadataForNode(request, node) : null;
        const metadataJson = explainMetadata ? JSON.stringify(explainMetadata) : undefined;

        if (node[0].textType === TextType.RESULT_SET) {
          try {
            const resultData: ResultData = JSON.parse(node[0].text);
            if (resultData.displayStyle?.type && resultData.displayStyle?.type !== 'table') {
              const aiMessage: ChatMessage = {
                sessionId,
                role: 'assistant',
                content: node[0].text,
                messageType: 'result-set',
                metadata: metadataJson,
              };
              await ChatService.saveMessage(sessionId, aiMessage);
              return;
            }
          } catch (error) {
            console.error('解析结果集 JSON 失败:', error);
          }
        }

        const nodeHtml = generateNodeHtml(node);
        const aiMessage: ChatMessage = {
          sessionId,
          role: 'assistant',
          content: nodeHtml,
          messageType: 'html',
          metadata: metadataJson,
        };
        await ChatService.saveMessage(sessionId, aiMessage);
      };

      const sendGraphRequest = async (request: GraphRequest) => {
        const sessionId = currentSession.value!.id;
        currentSession.value!.title;
        const sessionState = getSessionState(sessionId);
        try {
          // 准备流式请求
          isStreaming.value = true;
          nodeBlocks.value = [];

          let currentNodeName: string | null = null;
          let currentBlockIndex: number = -1;
          const pendingSavePromises: Promise<void>[] = [];

          // 重置报告状态
          resetReportState(sessionState, request);

          const saveNodeMessage = (node: GraphNodeResponse[]): Promise<void> => {
            return saveAssistantNodeMessage(sessionId, node, request).catch(error => {
              console.error('保存AI消息失败:', error);
            });
          };

          // 发送流式请求
          const persistBlockAt = async (blockIndex: number): Promise<void> => {
            if (blockIndex < 0 || !sessionState.nodeBlocks[blockIndex]) {
              return;
            }
            await saveNodeMessage(sessionState.nodeBlocks[blockIndex]);
            sessionState.persistedBlockCount = Math.max(
              sessionState.persistedBlockCount,
              blockIndex + 1,
            );
          };

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
              if (isClarifyMetadata(response.metadata)) {
                const nextPendingClarify = buildPendingClarifyState(response.metadata);
                sessionState.pendingClarify = nextPendingClarify;
                if (currentSession.value?.id === sessionId) {
                  pendingClarify.value = nextPendingClarify;
                }
              }

              // 检查是否是报告节点
              if (response.nodeName === 'ReportGeneratorNode') {
                const isNewNode: boolean =
                  currentNodeName === null || response.nodeName !== currentNodeName;

                if (isNewNode) {
                  // 保存上一个节点的消息（如果有）
                  if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                    const savePromise = persistBlockAt(currentBlockIndex);
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
              } else if (response.textType === TextType.RESULT_SET) {
                currentNodeName = 'result_set';
                if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                  const savePromise = persistBlockAt(currentBlockIndex);
                  pendingSavePromises.push(savePromise);
                }
                // 创建新的节点块
                const newBlock: GraphNodeResponse = {
                  ...response,
                  text: response.text,
                };
                sessionState.nodeBlocks.push([newBlock]);
                currentBlockIndex = sessionState.nodeBlocks.length - 1;
              } else {
                // 处理其他节点（同步处理逻辑）
                const isNewNode: boolean =
                  currentNodeName === null || response.nodeName !== currentNodeName;

                if (isNewNode) {
                  // 保存上一个节点的消息（如果有）
                  if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                    const savePromise = persistBlockAt(currentBlockIndex);
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
                scrollToBottom();
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
              sessionState.persistedBlockCount = 0;
              sessionState.closeStream = null;
              currentNodeName = null;
              // 出错时只有当前会话才重新加载
              if (currentSession.value?.id === sessionId) {
                isStreaming.value = false;
                await selectSession(currentSession.value);
              }
            },
            async () => {
              try {
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
                  sessionState.persistedBlockCount = 0;
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
                  sessionState.persistedBlockCount = 0;
                  if (currentSession.value?.id === sessionId) {
                    isStreaming.value = false;
                    nodeBlocks.value = [];
                  }
                } else {
                  // 其他节点，可能是错误或人类反馈模式
                  // 保存最后一个节点的消息（如果有）
                  if (currentBlockIndex >= 0 && sessionState.nodeBlocks[currentBlockIndex]) {
                    await persistBlockAt(currentBlockIndex);
                  }

                  // 所有节点处理完成
                  sessionState.isStreaming = false;
                  sessionState.persistedBlockCount = 0;
                  // 如果是当前显示的会话，同步到视图
                  if (currentSession.value?.id === sessionId) {
                    isStreaming.value = false;
                  }
                }

                currentNodeName = null;
                closeStream();
                // 只有当前会话才重新加载消息
                if (currentSession.value?.id === sessionId) {
                  await selectSession(currentSession.value);
                }
              } catch (error) {
                console.error('出现错误:', error);
              } finally {
                sessionState.isStreaming = false;
                sessionState.persistedBlockCount = 0;
                sessionState.closeStream = null;
                if (currentSession.value?.id === sessionId) {
                  isStreaming.value = false;
                }
              }
            },
          );
          // 保存closeStream函数到会话状态
          sessionState.closeStream = closeStream;
        } catch (error) {
          ElMessage.error('发送消息失败');
          console.error('发送消息失败:', error);
          sessionState.isStreaming = false;
          sessionState.persistedBlockCount = 0;
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

      // 服务器端下载html报告
      const downloadHtmlReportFromMessageByServer = async (content: string) => {
        if (!content) {
          ElMessage.warning('没有可下载的HTML报告');
          return;
        }
        if (!currentSession.value) {
          ElMessage.warning('当前没有会话信息');
          return;
        }
        try {
          await ChatService.downloadHtmlReport(currentSession.value.id, content);
          ElMessage.success('HTML报告下载成功');
        } catch (error) {
          console.error('下载HTML报告失败:', error);
          ElMessage.error('下载HTML报告失败');
        }
      };

      const openReportFullscreen = (content: string) => {
        fullscreenReportContent.value = content;
        showReportFullscreen.value = true;
      };

      const closeReportFullscreen = () => {
        showReportFullscreen.value = false;
        fullscreenReportContent.value = '';
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
            try {
              // 解析JSON字符串
              const resultData: ResultData = JSON.parse(node[idx].text);
              const resultSetData = resultData.resultSet;

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

              // 如果type是table，保持原有逻辑生成表格HTML
              // 否则返回空字符串，因为已经在模板中用ResultSetDisplay组件处理了
              if (resultData.displayStyle?.type === 'table' || !resultData.displayStyle?.type) {
                const tableHtml = generateResultSetTable(resultSetData, resultSetPageSize.value);
                content += tableHtml;
              }
              // 如果type不是table，不生成HTML，由模板中的ResultSetDisplay组件处理
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
        sessionState.persistedBlockCount = 0;
        sessionState.lastRequest = request;
        sessionState.htmlReportContent = '';
        sessionState.htmlReportSize = 0;
        sessionState.markdownReportContent = '';
      };

      type TraceAttributeEntry = { key: string; value: string };
      type FlattenedTraceSpan = {
        span: TraceSpan;
        depth: number;
        attributeEntries: TraceAttributeEntry[];
        searchableText: string;
      };
      type TraceMessageKind =
        | 'system'
        | 'user'
        | 'assistant'
        | 'tool-call'
        | 'tool-result'
        | 'other';
      type ParsedTraceMessage = {
        id: string;
        kind: TraceMessageKind;
        label: string;
        title: string;
        content: string;
        details: string;
        skills: string[];
      };

      const isTraceRecord = (value: unknown): value is Record<string, unknown> =>
        value !== null && typeof value === 'object' && !Array.isArray(value);

      const tryParseTraceJson = (value: string): unknown | null => {
        if (!isStructuredTraceValue(value)) {
          return null;
        }
        try {
          return JSON.parse(value);
        } catch (error) {
          return null;
        }
      };

      const createRuntimeRequestId = () => {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
          return crypto.randomUUID();
        }
        return `req-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
      };

      const parseMessageMetadata = (message: ChatMessage): MessageExplainMetadata | null => {
        if (!message.metadata) {
          return null;
        }
        try {
          const parsed = JSON.parse(message.metadata) as MessageExplainMetadata;
          return parsed && typeof parsed === 'object' ? parsed : null;
        } catch (error) {
          console.warn('解析消息 metadata 失败:', error);
          return null;
        }
      };

      const latestExplainMessage = computed<ChatMessage | null>(() => {
        for (let index = currentMessages.value.length - 1; index >= 0; index -= 1) {
          const message = currentMessages.value[index];
          if (message.role !== 'assistant') {
            continue;
          }
          const metadata = parseMessageMetadata(message);
          if (metadata?.explainAvailable && metadata.runtimeRequestId) {
            return message;
          }
        }
        return null;
      });

      const latestExplainRuntimeRequestId = computed(() => {
        if (!currentSession.value) {
          return null;
        }
        const sessionState = getSessionState(currentSession.value.id);
        if (sessionState.lastRequest?.runtimeRequestId) {
          return sessionState.lastRequest.runtimeRequestId;
        }
        const message = latestExplainMessage.value;
        if (!message) {
          return null;
        }
        return parseMessageMetadata(message)?.runtimeRequestId ?? null;
      });

      const loadAnswerExplainByRuntimeRequestId = async (runtimeRequestId: string) => {
        if (!currentSession.value) {
          return;
        }
        answerExplainVisible.value = true;
        answerExplainLoading.value = true;
        answerExplainError.value = '';
        try {
          answerExplain.value = await ChatService.getAnswerExplain(
            currentSession.value.id,
            runtimeRequestId,
          );
          // 保存到会话状态（包括 sessionStorage）
          saveViewToState(currentSession.value.id, {
            isStreaming,
            nodeBlocks,
            answerExplain,
            answerExplainVisible,
          });
        } catch (error: any) {
          answerExplain.value = null;
          if (error?.response?.status === 404) {
            answerExplainError.value = '当前回答还没有生成 explain 数据，请稍后再试。';
          } else {
            answerExplainError.value = '加载数据来源失败，请稍后重试。';
          }
          console.error('加载 answer explain 失败:', error);
        } finally {
          answerExplainLoading.value = false;
        }
      };

      const openLatestAnswerExplain = async () => {
        const runtimeRequestId = latestExplainRuntimeRequestId.value;
        if (!runtimeRequestId) {
          ElMessage.warning('当前会话还没有可查看的数据来源');
          return;
        }
        await loadAnswerExplainByRuntimeRequestId(runtimeRequestId);
      };

      const formatExplainValue = (value: unknown) => {
        if (value === null || value === undefined || value === '') {
          return '-';
        }
        if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
          return String(value);
        }
        try {
          return JSON.stringify(value, null, 2);
        } catch (error) {
          console.warn('格式化 explain 值失败:', error);
          return String(value);
        }
      };

      const summarizeExplainExecution = (explain: AnswerTraceExplain | null) => {
        if (!explain) {
          return '当前还没有可展示的执行说明。';
        }
        if (explain.datasource || explain.usedTables.length > 0 || explain.usedColumns.length > 0) {
          return '本轮回答访问了结构化数据源，下面展示的是实际使用到的表、字段、权限范围和统计信息。';
        }
        if (explain.knowledgeHits.length > 0) {
          return '本轮回答没有访问数据库，但命中了知识检索结果，回答受这些知识片段影响。';
        }
        if (explain.semanticHits.length > 0) {
          return '本轮回答没有直接查库，但命中了语义模型，用来帮助系统理解你的问题和业务字段。';
        }
        if (explain.toolSteps.length > 0) {
          return '本轮回答没有形成可展示的数据源明细，但系统执行过工具步骤，详细过程可在下方执行过程里查看。';
        }
        return '本轮回答没有访问数据库、知识库或其他可回放工具，当前结果主要来自模型直接生成。';
      };

      const stringifyTracePayload = (value: unknown) => {
        if (typeof value === 'string') {
          return value;
        }
        if (value === null || value === undefined) {
          return '';
        }
        try {
          return JSON.stringify(value, null, 2);
        } catch (error) {
          return String(value);
        }
      };

      const unwrapTraceTextEnvelope = (value: unknown): unknown => {
        if (!isTraceRecord(value)) {
          if (typeof value !== 'string') {
            return value;
          }
          const parsed = tryParseTraceJson(value);
          return parsed !== null ? unwrapTraceTextEnvelope(parsed) : value;
        }
        const keys = Object.keys(value);
        const textValue = typeof value.text === 'string' ? value.text.trim() : '';
        const isTextEnvelope =
          textValue.length > 0 &&
          keys.every(key => ['text', 'type', 'mimeType', 'metadata'].includes(key));
        if (!isTextEnvelope) {
          return value;
        }
        const parsedText = tryParseTraceJson(textValue);
        return parsedText !== null ? unwrapTraceTextEnvelope(parsedText) : textValue;
      };

      const unwrapTraceToolCallPayload = (value: Record<string, unknown>) => {
        if (isTraceRecord(value.param)) {
          return value.param;
        }
        return value;
      };

      const buildTraceToolCallContent = (value: Record<string, unknown>) => {
        const unwrappedValue = unwrapTraceToolCallPayload(value);
        const preferredPayload =
          unwrappedValue.input ??
          (isTraceRecord(unwrappedValue.metadata)
            ? unwrappedValue.metadata.arguments
            : undefined) ??
          unwrappedValue.arguments ??
          unwrappedValue.content ??
          unwrappedValue;
        if (typeof preferredPayload === 'string') {
          const parsed = tryParseTraceJson(preferredPayload);
          return parsed !== null ? stringifyTracePayload(parsed) : preferredPayload;
        }
        return stringifyTracePayload(preferredPayload);
      };

      const buildTraceToolResultContent = (value: Record<string, unknown>) => {
        const preferredPayload = unwrapTraceTextEnvelope(
          value.output ?? value.content ?? value.result ?? value,
        );
        return stringifyTracePayload(preferredPayload);
      };

      const traceMessageLabelMap: Record<TraceMessageKind, string> = {
        system: 'SYSTEM',
        user: 'USER',
        assistant: 'ASSISTANT',
        'tool-call': 'TOOL CALL',
        'tool-result': 'TOOL RESULT',
        other: 'OTHER',
      };

      const createParsedTraceMessage = (
        id: string,
        kind: TraceMessageKind,
        options: Partial<ParsedTraceMessage>,
      ): ParsedTraceMessage => ({
        id,
        kind,
        label: traceMessageLabelMap[kind],
        title: options.title ?? '',
        content: options.content ?? '',
        details: options.details ?? '',
        skills: options.skills ?? [],
      });

      const buildTraceAttributeEntries = (
        attributes: Record<string, string>,
      ): TraceAttributeEntry[] => {
        return Object.entries(attributes ?? {})
          .sort(([leftKey], [rightKey]) => leftKey.localeCompare(rightKey))
          .map(([key, value]) => ({
            key,
            value,
          }));
      };

      const flattenTraceSpans = (spans: TraceSpan[], depth = 0): FlattenedTraceSpan[] => {
        return spans.flatMap(span => {
          const attributeEntries = buildTraceAttributeEntries(span.attributes ?? {});
          const searchableText = [
            span.name,
            span.spanId,
            span.parentSpanId,
            span.kind,
            span.status,
            ...attributeEntries.flatMap(entry => [entry.key, entry.value]),
          ]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();
          return [
            {
              span,
              depth,
              attributeEntries,
              searchableText,
            },
            ...flattenTraceSpans(span.children ?? [], depth + 1),
          ];
        });
      };

      const flattenedTraceSpans = computed(() =>
        sessionTrace.value ? flattenTraceSpans(sessionTrace.value.rootSpans ?? []) : [],
      );

      const normalizedTraceSearchKeyword = computed(() =>
        traceSearchKeyword.value.trim().toLowerCase(),
      );

      const filteredTraceSpans = computed(() => {
        const keyword = normalizedTraceSearchKeyword.value;
        if (!keyword) {
          return flattenedTraceSpans.value;
        }
        return flattenedTraceSpans.value.filter(row => row.searchableText.includes(keyword));
      });

      const selectedTraceRow = computed(() => {
        if (!flattenedTraceSpans.value.length) {
          return null;
        }
        return (
          flattenedTraceSpans.value.find(row => row.span.spanId === selectedTraceSpanId.value) ??
          filteredTraceSpans.value[0] ??
          flattenedTraceSpans.value[0]
        );
      });

      const selectedTraceAttributeEntries = computed(() => {
        const row = selectedTraceRow.value;
        if (!row) {
          return [];
        }
        const keyword = normalizedTraceSearchKeyword.value;
        if (!keyword) {
          return row.attributeEntries;
        }
        return row.attributeEntries.filter(entry =>
          `${entry.key} ${entry.value}`.toLowerCase().includes(keyword),
        );
      });

      const parsedTraceConversations = computed(() => {
        const row = selectedTraceRow.value;
        if (!row) {
          return [];
        }

        // 只处理 agentscope.function.input 和 agentscope.function.output
        const inputEntry = row.attributeEntries.find(e => e.key === 'agentscope.function.input');
        const outputEntry = row.attributeEntries.find(e => e.key === 'agentscope.function.output');
        const nameEntry = row.attributeEntries.find(e => e.key === 'agentscope.function.name');

        if (inputEntry && outputEntry) {
          const inputParsed = tryParseTraceJson(inputEntry.value);
          const outputParsed = tryParseTraceJson(outputEntry.value);
          const toolName = nameEntry ? tryParseTraceJson(nameEntry.value) : null;

          const messages: ParsedTraceMessage[] = [];

          // 提取工具名称
          let name = '工具调用';
          if (typeof toolName === 'string') {
            name = toolName;
          } else if (isTraceRecord(inputParsed) && isTraceRecord(inputParsed.param)) {
            name = typeof inputParsed.param.name === 'string' ? inputParsed.param.name : name;
          }

          // 创建 TOOL CALL 消息
          if (inputParsed !== null) {
            const inputContent =
              isTraceRecord(inputParsed) && isTraceRecord(inputParsed.param)
                ? buildTraceToolCallContent(inputParsed.param)
                : stringifyTracePayload(inputParsed);

            messages.push(
              createParsedTraceMessage('agentscope-function-call', 'tool-call', {
                title: name,
                content: inputContent,
                details: '',
              }),
            );
          }

          // 创建 TOOL RESULT 消息（处理截断情况）
          let outputContent: string;

          if (outputParsed !== null) {
            // JSON 解析成功
            outputContent = buildTraceToolResultContent(
              isTraceRecord(outputParsed) ? outputParsed : { output: outputParsed },
            );
          } else {
            // JSON 解析失败（可能被截断），显示原始内容
            outputContent = outputEntry.value;

            // 如果内容被截断，添加提示
            if (outputEntry.value.endsWith('...') || outputEntry.value.length > 10000) {
              outputContent += '\n\n[注意：内容可能被截断，请在属性详情中查看完整内容]';
            }
          }

          messages.push(
            createParsedTraceMessage('agentscope-function-result', 'tool-result', {
              title: name,
              content: outputContent,
              details: '',
            }),
          );

          if (messages.length > 0) {
            return [
              {
                attributeKey: 'agentscope.function',
                title: 'agentscope.function · messages',
                messages,
              },
            ];
          }
        }

        // 如果没有找到 agentscope.function 属性，返回空
        return [];
      });

      const selectTraceSpan = (spanId: string) => {
        selectedTraceSpanId.value = spanId;
      };

      const formatTraceDuration = (durationMs: number) => {
        if (durationMs < 1000) {
          return `${durationMs} ms`;
        }
        if (durationMs < 10000) {
          return `${(durationMs / 1000).toFixed(2)} s`;
        }
        return `${(durationMs / 1000).toFixed(1)} s`;
      };

      const formatTraceTime = (epochMs: number) => {
        if (!epochMs) {
          return '--';
        }
        return new Date(epochMs).toLocaleString();
      };

      const formatTraceOffset = (epochMs: number) => {
        if (!sessionTrace.value?.startEpochMs || !epochMs) {
          return '--';
        }
        const offset = Math.max(0, epochMs - sessionTrace.value.startEpochMs);
        return `+${formatTraceDuration(offset)}`;
      };

      const isStructuredTraceValue = (value: string) => {
        const normalizedValue = value?.trim();
        return Boolean(
          normalizedValue &&
            ((normalizedValue.startsWith('{') && normalizedValue.endsWith('}')) ||
              (normalizedValue.startsWith('[') && normalizedValue.endsWith(']'))),
        );
      };

      const formatStructuredTraceValue = (value: string) => {
        if (!isStructuredTraceValue(value)) {
          return value;
        }
        try {
          return JSON.stringify(JSON.parse(value), null, 2);
        } catch (error) {
          return value;
        }
      };

      const loadLatestTrace = async () => {
        if (!currentSession.value) {
          sessionTrace.value = null;
          traceError.value = '当前没有可查看 trace 的会话';
          return;
        }
        traceLoading.value = true;
        traceError.value = '';
        try {
          sessionTrace.value = await ChatService.getSessionTrace(currentSession.value.id);
          selectedTraceSpanId.value = sessionTrace.value.rootSpans?.[0]?.spanId ?? '';
        } catch (error: any) {
          sessionTrace.value = null;
          selectedTraceSpanId.value = '';
          if (error?.response?.status === 404) {
            traceError.value = '当前会话还没有最近一次 trace，请先执行一轮对话。';
          } else {
            traceError.value = '加载 trace 失败，请稍后重试。';
          }
          console.error('加载 trace 失败:', error);
        } finally {
          traceLoading.value = false;
        }
      };

      const openTraceDialog = async () => {
        traceDialogVisible.value = true;
        await loadLatestTrace();
      };

      const refreshTrace = async () => {
        await loadLatestTrace();
      };

      const scrollToBottom = () => {
        nextTick(() => {
          if (chatContainer.value) {
            chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
          }
        });
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
              return saveAssistantNodeMessage(sessionId, node, sessionState.lastRequest).catch(
                error => {
                  console.error('保存AI消息失败:', error);
                },
              );
            };

            // 保存所有未保存的节点块
            const basePersistedCount = sessionState.persistedBlockCount;
            const unsavedBlocks = sessionState.nodeBlocks.slice(basePersistedCount);
            const savePromises = unsavedBlocks.map((block, index) =>
              saveNodeMessage(block).then(() => {
                sessionState.persistedBlockCount = Math.max(
                  sessionState.persistedBlockCount,
                  basePersistedCount + index + 1,
                );
              }),
            );
            await Promise.all(savePromises).catch(error => {
              console.error('保存节点消息时出错:', error);
            });
          }

          // 清理流式状态
          sessionState.isStreaming = false;
          sessionState.nodeBlocks = [];
          sessionState.persistedBlockCount = 0;
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
          sessionState.persistedBlockCount = 0;
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

      // 从节点块中提取 Markdown 内容
      const getMarkdownContentFromNode = (node: GraphNodeResponse[]): string => {
        if (!node || node.length === 0) {
          return '';
        }

        // 如果是 ReportGeneratorNode 且类型为 MARK_DOWN，从 sessionState 获取完整内容
        // 这样可以实时显示流式接收到的 markdown 内容
        const firstNode = node[0];
        if (firstNode.nodeName === 'ReportGeneratorNode' && firstNode.textType === 'MARK_DOWN') {
          const sessionId = currentSession.value?.id;
          if (sessionId) {
            const sessionState = getSessionState(sessionId);
            // 返回实时更新的 markdown 内容
            return sessionState.markdownReportContent || '';
          }
        }

        // 否则从节点中提取所有 MARK_DOWN 类型的文本
        let markdown = '';
        for (let idx = 0; idx < node.length; idx++) {
          if (node[idx].textType === 'MARK_DOWN') {
            let p = idx;
            for (; p < node.length; p++) {
              if (node[p].textType !== 'MARK_DOWN') {
                break;
              }
              markdown += node[p].text;
            }
            if (p < node.length) {
              idx = p - 1;
            } else {
              break;
            }
          }
        }

        return markdown;
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
        isSubmittingMessage,
        pendingClarify,
        requestOptions,
        showReportFullscreen,
        fullscreenReportContent,
        inputControlsCollapsed,
        traceDialogVisible,
        traceLoading,
        traceError,
        sessionTrace,
        answerExplainVisible,
        answerExplainLoading,
        answerExplainError,
        answerExplain,
        latestExplainMessage,
        latestExplainRuntimeRequestId,
        chatContainer,
        nodeBlocks,
        agentId,
        resultSetPageSize,
        options,
        traceSearchKeyword,
        flattenedTraceSpans,
        filteredTraceSpans,
        selectedTraceSpanId,
        selectedTraceRow,
        selectedTraceAttributeEntries,
        parsedTraceConversations,
        formatTraceDuration,
        formatTraceTime,
        formatTraceOffset,
        formatExplainValue,
        summarizeExplainExecution,
        isStructuredTraceValue,
        formatStructuredTraceValue,
        getMarkdownContentFromNode,
        selectSession,
        sendMessage,
        cancelPendingClarify,
        applyClarifyAssumption,
        formatMessageContent,
        formatNodeContent,
        generateNodeHtml,
        openReportFullscreen,
        closeReportFullscreen,
        downloadMarkdownReportFromMessage,
        downloadHtmlReportFromMessageByServer,
        markdownToHtml,
        resetReportState,
        handlePresetQuestionClick,
        stopStreaming,
        openLatestAnswerExplain,
        openTraceDialog,
        refreshTrace,
        selectTraceSpan,
        deleteSessionState,
      };
    },
  });
</script>

<style scoped>
  /* CSS 变量定义 */
  :root {
    --trace-primary-color: #409eff;
    --trace-primary-light: #66b1ff;
    --trace-border-color: #e8f1fa;
    --trace-border-hover: #b8daff;
    --trace-bg-gradient-start: #ffffff;
    --trace-bg-gradient-end: #f8fcff;
    --trace-text-primary: #1e3a5f;
    --trace-text-secondary: #5a7291;
    --trace-text-meta: #6b7f95;
    --trace-shadow-sm: 0 2px 8px rgba(37, 99, 235, 0.08);
    --trace-shadow-md: 0 4px 16px rgba(0, 0, 0, 0.06);
    --trace-shadow-lg: 0 8px 24px rgba(64, 158, 255, 0.15);
    --trace-shadow-hover: 0 4px 12px rgba(37, 99, 235, 0.15);
    --trace-indent-size: 32px;
    --trace-base-padding: 20px;
    --trace-border-radius: 16px;
    --trace-border-radius-lg: 20px;
    --trace-transition: all 0.3s ease;
  }

  /* 聊天容器样式 */
  .chat-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
    margin-bottom: 20px;
  }

  .clarify-banner {
    margin-bottom: 12px;
    padding: 14px 16px;
    border: 1px solid #f7c56b;
    border-radius: 12px;
    background: linear-gradient(135deg, #fff8e8 0%, #fff3d4 100%);
    box-shadow: 0 8px 18px rgba(196, 138, 18, 0.08);
  }

  .clarify-banner-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 8px;
  }

  .clarify-banner-title {
    font-size: 14px;
    font-weight: 700;
    color: #7a4a00;
  }

  .clarify-banner-risk {
    font-size: 12px;
    font-weight: 600;
    color: #a86100;
  }

  .clarify-banner-body {
    font-size: 13px;
    line-height: 1.6;
    color: #7a5618;
  }

  .clarify-banner-tags,
  .clarify-banner-assumptions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }

  .clarify-banner-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 10px;
    font-size: 12px;
    color: #8a6528;
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

  /* 当 agent-response-content 包含 Markdown 组件时，重置样式 */
  .agent-response-content .markdown-container {
    line-height: 1.4;
    white-space: normal;
    font-family: inherit;
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

  /* Markdown报告消息样式 */
  .markdown-report-message {
    background: white;
    border: 1px solid #e8e8e8;
    border-radius: 12px;
    padding: 16px;
    margin-bottom: 16px;
  }

  .markdown-report-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;
  }

  .markdown-report-content {
    margin-top: 16px;
  }

  .report-info {
    display: flex;
    align-items: center;
    gap: 12px;
    color: #409eff;
    font-size: 16px;
    font-weight: 500;
  }

  .report-format-inline {
    margin-left: 8px;
  }

  /* 报告全屏样式 */
  .report-fullscreen-overlay {
    position: fixed;
    inset: 0;
    z-index: 9999;
    background: rgba(0, 0, 0, 0.7);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }

  .report-fullscreen-container {
    width: 100%;
    max-width: 1200px;
    height: 90vh;
    background: white;
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  }

  .report-fullscreen-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 24px;
    border-bottom: 1px solid #e8e8e8;
    background: #f8f9fa;
    flex-shrink: 0;
  }

  .report-fullscreen-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }

  .report-fullscreen-close {
    flex-shrink: 0;
  }

  .report-fullscreen-content {
    flex: 1;
    overflow: auto;
    padding: 24px;
  }

  .report-fullscreen-body {
    min-height: 100%;
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
    border-bottom: 1px solid #f0f0f0;
  }

  .input-controls-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    cursor: pointer;
    user-select: none;
    color: #606266;
    font-size: 14px;
  }

  .input-controls-header:hover {
    color: #409eff;
  }

  .input-controls-title {
    font-weight: 500;
  }

  .input-controls-toggle-btn {
    flex-shrink: 0;
  }

  .input-controls-toggle-btn .input-controls-toggle-icon {
    margin-right: 4px;
    transition: transform 0.2s ease;
  }

  .input-controls-toggle-btn.collapsed .input-controls-toggle-icon {
    transform: rotate(-90deg);
  }

  .input-controls-body {
    padding-bottom: 12px;
  }

  .switch-group {
    display: flex;
    flex-wrap: wrap;
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

  .message-rich-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  /* 数据来源面板优化 */
  .answer-explain-panel {
    display: flex;
    flex-direction: column;
    gap: 24px;
    padding-right: 6px;
  }

  .answer-explain-summary {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    padding: 16px;
    background: linear-gradient(135deg, #f0f7ff 0%, #e8f4ff 100%);
    border-radius: 16px;
    border: 1px solid #d0e7ff;
  }

  .answer-explain-pill {
    display: inline-flex;
    align-items: center;
    min-height: 36px;
    padding: 0 16px;
    border-radius: 999px;
    background: linear-gradient(135deg, #ffffff 0%, #f5fbff 100%);
    border: 1px solid #b8daff;
    color: #2563eb;
    font-size: 13px;
    font-weight: 600;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
    transition: all 0.3s ease;
  }

  .answer-explain-pill:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
    border-color: #409eff;
  }

  .answer-explain-section {
    border: 1px solid #e0ebf8;
    border-radius: 20px;
    background: linear-gradient(180deg, #ffffff 0%, #fafcff 100%);
    padding: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    transition: all 0.3s ease;
  }

  .answer-explain-section:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
    border-color: #c9dff5;
  }

  .answer-explain-section-title {
    font-size: 16px;
    font-weight: 700;
    color: #1e3a5f;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .answer-explain-section-title::before {
    content: '';
    display: inline-block;
    width: 4px;
    height: 20px;
    background: linear-gradient(180deg, #409eff 0%, #66b1ff 100%);
    border-radius: 2px;
  }

  .answer-explain-card-list,
  .answer-explain-step-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .answer-explain-card,
  .answer-explain-step {
    border: 1px solid #e8f1fa;
    border-radius: 16px;
    background: linear-gradient(135deg, #ffffff 0%, #f8fcff 100%);
    padding: 16px 18px;
    transition: all 0.3s ease;
    position: relative;
    overflow: hidden;
  }

  .answer-explain-card::before,
  .answer-explain-step::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: linear-gradient(180deg, #409eff 0%, #66b1ff 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  .answer-explain-card:hover,
  .answer-explain-step:hover {
    border-color: #b8daff;
    box-shadow: 0 4px 16px rgba(64, 158, 255, 0.12);
    transform: translateX(4px);
  }

  .answer-explain-card:hover::before,
  .answer-explain-step:hover::before {
    opacity: 1;
  }

  .answer-explain-card-title,
  .answer-explain-step-title {
    font-weight: 700;
    font-size: 14px;
    color: #1e3a5f;
  }

  .answer-explain-card-meta,
  .answer-explain-step-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 8px;
    color: #6b7f95;
    font-size: 12px;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
  }

  .answer-explain-card-body,
  .answer-explain-step-body {
    margin-top: 12px;
    color: #3d5a7a;
    line-height: 1.8;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .answer-explain-code,
  .answer-explain-step-detail {
    margin: 12px 0 0;
    padding: 16px;
    border-radius: 12px;
    background: linear-gradient(135deg, #1a1f2e 0%, #0f1419 100%);
    color: #e6f1ff;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 13px;
    line-height: 1.8;
    box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.3);
    border: 1px solid #2a3441;
  }

  .answer-explain-sql-summary {
    color: #3d5a7a;
    line-height: 1.9;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .answer-explain-kv {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .answer-explain-kv-row {
    display: grid;
    grid-template-columns: 140px minmax(0, 1fr);
    gap: 16px;
    align-items: start;
    padding: 12px 0;
    border-bottom: 1px solid #eef4fa;
    transition: background 0.2s ease;
  }

  .answer-explain-kv-row:hover {
    background: linear-gradient(90deg, transparent 0%, #f5f9ff 100%);
  }

  .answer-explain-kv-row:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  .answer-explain-kv-key {
    color: #5a7291;
    font-size: 13px;
    font-weight: 600;
  }

  .answer-explain-kv-value {
    color: #1e3a5f;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 13px;
    line-height: 1.8;
  }

  .answer-explain-warning-list {
    margin: 0;
    padding-left: 20px;
    color: #5a6e83;
    line-height: 1.9;
  }

  /* Trace 按钮优化 */
  .trace-button {
    height: 40px;
    padding: 0 16px;
    border-radius: 999px;
    flex-shrink: 0;
    font-weight: 600;
    transition: all 0.3s ease;
  }

  .trace-button:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
  }

  /* Trace 工具栏优化 */
  .trace-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 20px;
    margin-bottom: 20px;
    padding: 16px;
    background: linear-gradient(135deg, #f5f9ff 0%, #eef6ff 100%);
    border-radius: 16px;
    border: 1px solid #d0e7ff;
  }

  .trace-summary {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .trace-summary-pill {
    display: inline-flex;
    align-items: center;
    min-height: 36px;
    padding: 0 16px;
    border: 1px solid #b8daff;
    border-radius: 999px;
    background: linear-gradient(135deg, #ffffff 0%, #f5fbff 100%);
    color: #2563eb;
    font-size: 13px;
    font-weight: 600;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
    transition: all 0.3s ease;
  }

  .trace-summary-pill:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
    border-color: #409eff;
  }

  .trace-toolbar-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .trace-search-input {
    width: 300px;
  }

  .trace-alert {
    margin-bottom: 16px;
  }

  /* Trace 探索器优化 */
  .trace-explorer {
    display: grid;
    grid-template-columns: minmax(380px, 45%) minmax(440px, 1fr);
    gap: 20px;
    min-height: 200vh;
  }

  .trace-pane {
    border: 1px solid #d9e8f7;
    border-radius: 20px;
    background: linear-gradient(180deg, #ffffff 0%, #f8fcff 100%);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
    overflow: hidden;
    transition: all 0.3s ease;
  }

  .trace-pane:hover {
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.1);
  }

  .trace-pane-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    padding: 18px 20px 14px;
    background: linear-gradient(135deg, #f5f9ff 0%, #eef6ff 100%);
    border-bottom: 2px solid #e0ebf8;
  }

  .trace-pane-title {
    font-size: 15px;
    font-weight: 700;
    color: #1e3a5f;
  }

  .trace-pane-count {
    color: #5a7291;
    font-size: 13px;
    font-weight: 600;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    padding: 4px 12px;
    background: rgba(64, 158, 255, 0.1);
    border-radius: 999px;
  }

  /* Trace 列表优化 - 树形展示 */
  .trace-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: calc(75vh - 60px);
    overflow: auto;
    padding: 16px;
    position: relative;
  }

  .trace-row {
    appearance: none;
    width: 100%;
    border: 2px solid #e8f1fa;
    border-radius: 16px;
    padding: 16px 18px;
    background: linear-gradient(135deg, #ffffff 0%, #fafcff 100%);
    text-align: left;
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: visible;
    margin-left: 0;
  }

  /* 树形连接线 - 垂直线 */
  .trace-row::before {
    content: '';
    position: absolute;
    left: -20px;
    top: 50%;
    width: 16px;
    height: 2px;
    background: linear-gradient(90deg, transparent 0%, #c9dff5 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  /* 树形连接线 - 水平线（用于显示层级） */
  .trace-row::after {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: linear-gradient(180deg, #409eff 0%, #66b1ff 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
    border-radius: 16px 0 0 16px;
  }

  /* 为有深度的节点显示连接线 */
  .trace-row[style*='paddingLeft']::before {
    opacity: 0.6;
  }

  .trace-row:hover {
    border-color: #409eff;
    box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
    transform: translateY(-2px);
    z-index: 1;
  }

  .trace-row:hover::after {
    opacity: 1;
  }

  .trace-row.is-selected {
    border-color: #409eff;
    box-shadow: 0 12px 32px rgba(64, 158, 255, 0.2);
    background: linear-gradient(135deg, #f0f7ff 0%, #e8f4ff 100%);
    z-index: 2;
  }

  .trace-row.is-selected::after {
    opacity: 1;
    width: 5px;
  }

  .trace-row.is-error {
    border-color: #f56c6c;
    background: linear-gradient(135deg, #fff5f5 0%, #ffe8e8 100%);
  }

  .trace-row.is-error::after {
    background: linear-gradient(180deg, #f56c6c 0%, #ff8080 100%);
  }

  .trace-row.is-error:hover {
    border-color: #f56c6c;
    box-shadow: 0 8px 24px rgba(245, 108, 108, 0.2);
  }

  .trace-row-main {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .trace-row-name {
    font-weight: 700;
    font-size: 14px;
    color: #1e3a5f;
  }

  .trace-row-duration {
    color: #409eff;
    font-size: 13px;
    font-weight: 700;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    padding: 2px 10px;
    background: rgba(64, 158, 255, 0.1);
    border-radius: 999px;
  }

  .trace-row-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    margin-top: 10px;
    color: #6b7f95;
    font-size: 12px;
    word-break: break-all;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
  }

  .trace-row-meta span {
    padding: 2px 8px;
    background: rgba(107, 127, 149, 0.08);
    border-radius: 6px;
  }

  /* Trace 详情面板优化 */
  .trace-pane-detail {
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  .trace-detail-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 20px;
    padding: 20px 22px 16px;
    background: linear-gradient(135deg, #f5f9ff 0%, #eef6ff 100%);
    border-bottom: 2px solid #e0ebf8;
  }

  .trace-detail-title {
    font-size: 20px;
    font-weight: 700;
    color: #1e3a5f;
    line-height: 1.4;
  }

  .trace-detail-subtitle {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 10px;
    color: #5a7291;
    font-size: 12px;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
  }

  .trace-detail-subtitle span {
    padding: 4px 10px;
    background: rgba(90, 114, 145, 0.1);
    border-radius: 6px;
  }

  .trace-detail-tags {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .trace-descriptions {
    padding: 20px 22px 0;
  }

  /* Trace 消息面板优化 */
  .trace-message-panel {
    padding: 20px 22px 0;
  }

  .trace-message-groups {
    display: flex;
    flex-direction: column;
    gap: 16px;
    margin-top: 14px;
  }

  .trace-message-group {
    border: 2px solid #e0ebf8;
    border-radius: 16px;
    overflow: hidden;
    background: #ffffff;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    transition: all 0.3s ease;
  }

  .trace-message-group:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
    border-color: #c9dff5;
  }

  .trace-message-group-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 14px;
    padding: 14px 16px;
    background: linear-gradient(135deg, #f5f9ff 0%, #eef6ff 100%);
    border-bottom: 2px solid #e0ebf8;
  }

  .trace-message-group-title {
    color: #1e3a5f;
    font-size: 14px;
    font-weight: 700;
  }

  .trace-message-group-meta {
    color: #5a7291;
    font-size: 12px;
    font-weight: 600;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    padding: 4px 12px;
    background: rgba(64, 158, 255, 0.1);
    border-radius: 999px;
  }

  .trace-message-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 16px;
  }

  .trace-message-item {
    display: grid;
    grid-template-columns: 100px minmax(0, 1fr);
    gap: 14px;
    align-items: start;
  }

  .trace-message-role {
    display: flex;
    justify-content: center;
    padding-top: 6px;
  }

  .trace-message-role-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 80px;
    min-height: 32px;
    padding: 0 12px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
  }

  .trace-message-role-badge:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  }

  .trace-message-item.is-system .trace-message-role-badge {
    color: #92400e;
    background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
    border: 1px solid #fbbf24;
  }

  .trace-message-item.is-user .trace-message-role-badge {
    color: #1e40af;
    background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
    border: 1px solid #60a5fa;
  }

  .trace-message-item.is-assistant .trace-message-role-badge {
    color: #065f46;
    background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
    border: 1px solid #34d399;
  }

  .trace-message-item.is-tool-call .trace-message-role-badge {
    color: #9a3412;
    background: linear-gradient(135deg, #fed7aa 0%, #fdba74 100%);
    border: 1px solid #fb923c;
  }

  .trace-message-item.is-tool-result .trace-message-role-badge {
    color: #6b21a8;
    background: linear-gradient(135deg, #e9d5ff 0%, #d8b4fe 100%);
    border: 1px solid #a855f7;
  }

  .trace-message-item.is-other .trace-message-role-badge {
    color: #374151;
    background: linear-gradient(135deg, #e5e7eb 0%, #d1d5db 100%);
    border: 1px solid #9ca3af;
  }

  .trace-message-body {
    padding: 16px 18px;
    border-radius: 16px;
    border: 2px solid #e8f1fa;
    background: #ffffff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    transition: all 0.3s ease;
  }

  .trace-message-body:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    border-color: #d0e7ff;
  }

  .trace-message-item.is-user .trace-message-body {
    background: linear-gradient(135deg, #f0f7ff 0%, #e8f4ff 100%);
    border-color: #b8daff;
  }

  .trace-message-item.is-assistant .trace-message-body {
    background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
    border-color: #bbf7d0;
  }

  .trace-message-item.is-system .trace-message-body {
    background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
    border-color: #fde68a;
  }

  .trace-message-item.is-tool-call .trace-message-body {
    background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
    border-color: #fed7aa;
  }

  .trace-message-item.is-tool-result .trace-message-body {
    background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
    border-color: #e9d5ff;
  }

  .trace-message-title {
    color: #1e3a5f;
    font-size: 14px;
    font-weight: 700;
    margin-bottom: 10px;
  }

  .trace-message-skills {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
  }

  .trace-skill-chip {
    display: inline-flex;
    align-items: center;
    padding: 0 12px;
    min-height: 28px;
    border-radius: 999px;
    background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
    color: #1e40af;
    font-size: 11px;
    font-weight: 700;
    border: 1px solid #93c5fd;
    box-shadow: 0 2px 6px rgba(30, 64, 175, 0.1);
    transition: all 0.3s ease;
  }

  .trace-skill-chip:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 10px rgba(30, 64, 175, 0.15);
  }

  .trace-message-content {
    color: #1e3a5f;
    font-size: 13px;
    line-height: 1.8;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .trace-message-content-structured,
  .trace-message-details {
    margin: 12px 0 0;
    padding: 14px;
    border-radius: 12px;
    background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
    border: 1px solid #cbd5e1;
    overflow: auto;
    font-size: 12px;
    line-height: 1.7;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    white-space: pre-wrap;
    word-break: break-word;
    box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.06);
  }

  /* Trace 属性面板优化 */
  .trace-attribute-panel {
    padding: 20px 22px;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .trace-attribute-table {
    border: 2px solid #e0ebf8;
    border-radius: 16px;
    overflow: hidden;
    background: #ffffff;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    transition: all 0.3s ease;
  }

  .trace-attribute-table:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
    border-color: #c9dff5;
  }

  .trace-attribute-table-header,
  .trace-attribute-row {
    display: grid;
    grid-template-columns: minmax(240px, 280px) minmax(0, 1fr);
  }

  .trace-attribute-table-header {
    background: linear-gradient(135deg, #f5f9ff 0%, #eef6ff 100%);
    color: #1e3a5f;
    font-size: 13px;
    font-weight: 700;
    border-bottom: 2px solid #e0ebf8;
  }

  .trace-attribute-table-header span,
  .trace-attribute-key,
  .trace-attribute-value {
    padding: 14px 16px;
  }

  .trace-attribute-row + .trace-attribute-row {
    border-top: 1px solid #eef4fa;
  }

  .trace-attribute-row {
    transition: background 0.2s ease;
  }

  .trace-attribute-row:hover {
    background: linear-gradient(90deg, #f8fcff 0%, #f0f7ff 100%);
  }

  .trace-attribute-key {
    color: #1e3a5f;
    font-size: 13px;
    font-weight: 600;
    word-break: break-all;
    background: linear-gradient(135deg, #fafcff 0%, #f5f9ff 100%);
    border-right: 2px solid #e0ebf8;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
  }

  .trace-attribute-value {
    color: #3d5a7a;
    font-size: 13px;
    line-height: 1.8;
    word-break: break-word;
    white-space: pre-wrap;
  }

  .trace-attribute-value-structured {
    margin: 0;
    padding: 14px;
    overflow: auto;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.06);
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

    .trace-toolbar {
      flex-direction: column;
      align-items: stretch;
      gap: 12px;
    }

    .trace-toolbar-actions {
      flex-direction: column;
      align-items: stretch;
    }

    .trace-search-input {
      width: 100%;
    }

    .trace-explorer {
      grid-template-columns: 1fr;
      gap: 16px;
    }

    .answer-explain-kv-row {
      grid-template-columns: 1fr;
      gap: 8px;
    }

    .trace-detail-header {
      flex-direction: column;
      align-items: stretch;
      gap: 12px;
    }

    .trace-message-item {
      grid-template-columns: 1fr;
      gap: 10px;
    }

    .trace-message-role {
      justify-content: flex-start;
      padding-top: 0;
    }

    .trace-message-group-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 8px;
    }

    .trace-attribute-table-header,
    .trace-attribute-row {
      grid-template-columns: 1fr;
    }

    .trace-attribute-key {
      border-right: none;
      border-bottom: 1px solid #e0ebf8;
    }

    .answer-explain-summary {
      padding: 12px;
    }

    .answer-explain-pill {
      min-height: 32px;
      padding: 0 12px;
      font-size: 12px;
    }

    .trace-summary-pill {
      min-height: 32px;
      padding: 0 12px;
      font-size: 12px;
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

  .result-set-message {
    width: 100%;
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
