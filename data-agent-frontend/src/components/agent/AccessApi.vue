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
  <div class="access-api">
    <section class="section">
      <h3>访问 API Key</h3>
      <p class="desc">为该智能体生成并管理 API Key，用于外部系统访问。</p>

      <div class="card">
        <div class="row">
          <div class="label">API Key 状态</div>
          <el-switch
            v-model="apiKeyEnabled"
            :disabled="!apiKey"
            active-text="已启用"
            inactive-text="已禁用"
            @change="handleToggle"
          />
          <el-tag v-if="!apiKey" type="info" size="small" class="tag">未生成</el-tag>
        </div>

        <div class="row key-row">
          <div class="label">当前 Key</div>
          <el-input
            v-model="displayKey"
            class="key-input"
            readonly
            placeholder="尚未生成 API Key"
          />
          <el-button type="primary" @click="handleGenerate" :loading="loading.generate">
            {{ apiKey ? '重新生成' : '生成 Key' }}
          </el-button>
          <el-button @click="handleReset" :disabled="!apiKey" :loading="loading.reset">
            重置
          </el-button>
          <el-button @click="handleDelete" :disabled="!apiKey" :loading="loading.delete">
            删除
          </el-button>
          <el-button @click="handleCopy" :disabled="!apiKey || !canCopy">复制</el-button>
          <el-button @click="toggleMask" :disabled="!apiKey">
            {{ masked ? '显示' : '隐藏' }}
          </el-button>
        </div>

        <el-alert
          v-if="!canCopy && apiKey"
          type="info"
          :closable="false"
          show-icon
          title="为安全起见，已生成/重置时才显示完整 Key，之后仅显示掩码。如需复制请重新生成/重置。"
        />
      </div>
    </section>

    <section class="section">
      <h3>调用示例</h3>
      <p class="desc">使用 `X-API-Key` 请求头调用会话接口。</p>
      <el-tabs v-model="exampleTab">
        <el-tab-pane label="curl" name="curl">
          <pre class="code"><code>{{ curlExample }}</code></pre>
        </el-tab-pane>
        <el-tab-pane label="JavaScript" name="js">
          <pre class="code"><code>{{ jsExample }}</code></pre>
        </el-tab-pane>
        <el-tab-pane label="Python" name="py">
          <pre class="code"><code>{{ pyExample }}</code></pre>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script lang="ts">
  import { defineComponent, ref, computed, onMounted } from 'vue';
  import { useRoute } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import AgentService from '@/services/agent';

  export default defineComponent({
    name: 'AgentAccessApi',
    props: {
      agentId: {
        type: [Number, String],
        required: false,
        default: null,
      },
    },
    setup(props) {
      const route = useRoute();
      const resolvedAgentId = computed(() => Number(props.agentId ?? route.params.id));

      const apiKey = ref<string | null>(null);
      const apiKeyEnabled = ref<boolean>(false);
      const masked = ref(true);
      const canCopy = ref(false);
      const loading = ref({
        generate: false,
        reset: false,
        delete: false,
        toggle: false,
        fetch: false,
      });
      const exampleTab = ref('curl');

      const maskKey = (key: string) => {
        if (!key) return '';
        if (key.startsWith('****')) return key;
        if (key.length <= 8) return '****';
        return '****' + key.slice(-4);
      };

      const displayKey = computed(() => {
        if (!apiKey.value) return '';
        return masked.value ? maskKey(apiKey.value) : apiKey.value;
      });

      const curlExample = computed(() => {
        const base = window.location.origin;
        const id = resolvedAgentId.value;
        return `# 创建会话
curl -X POST "${base}/api/agent/${id}/sessions" \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: <your_api_key>" \\
  -d '{"title":"demo"}'

# 发送消息
curl -X POST "${base}/api/sessions/<sessionId>/messages" \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: <your_api_key>" \\
  -d '{"role":"user","content":"给我一个示例","messageType":"text"}'`;
      });

      const jsExample = computed(() => {
        const base = window.location.origin;
        const id = resolvedAgentId.value;
        return String.raw`const apiKey = '<your_api_key>';
const baseUrl = '${base}/api';
const agentId = ${id};

(async () => {
  // 创建会话
  const sessionRes = await fetch(\`${'${baseUrl}'}/agent/${'${agentId}'}/sessions\`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': apiKey,
    },
    body: JSON.stringify({ title: 'demo' }),
  });
  const session = await sessionRes.json();

  // 发送消息
  await fetch(\`${'${baseUrl}'}/sessions/${'${session.id}'}/messages\`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': apiKey,
    },
    body: JSON.stringify({ role: 'user', content: '你好', messageType: 'text' }),
  });
})();`;
      });

      const pyExample = computed(() => {
        const base = window.location.origin;
        const id = resolvedAgentId.value;
        return `import requests

api_key = '<your_api_key>'
base_url = '${base}/api'

headers = {
    'Content-Type': 'application/json',
    'X-API-Key': api_key,
}

# 创建会话
session_resp = requests.post(
    f"{base_url}/agent/${id}/sessions",
    headers=headers,
    json={"title": "demo"},
)
session_id = session_resp.json().get("id")

# 发送消息
requests.post(
    f"{base_url}/sessions/{session_id}/messages",
    headers=headers,
    json={"role": "user", "content": "你好", "messageType": "text"},
)
`;
      });

      const loadApiKey = async () => {
        loading.value.fetch = true;
        try {
          const res = await AgentService.getApiKey(resolvedAgentId.value);
          apiKey.value = res?.apiKey ?? null;
          apiKeyEnabled.value = Boolean(res?.apiKeyEnabled);
          masked.value = true;
          canCopy.value = false;
        } catch (e) {
          ElMessage.error('获取 API Key 失败');
        } finally {
          loading.value.fetch = false;
        }
      };

      const handleGenerate = async () => {
        loading.value.generate = true;
        try {
          const res = await AgentService.generateApiKey(resolvedAgentId.value);
          apiKey.value = res.apiKey;
          apiKeyEnabled.value = Boolean(res.apiKeyEnabled);
          masked.value = false;
          canCopy.value = true;
          ElMessage.success('已生成 API Key');
        } catch (e) {
          ElMessage.error('生成失败');
        } finally {
          loading.value.generate = false;
        }
      };

      const handleReset = async () => {
        if (!apiKey.value) {
          await handleGenerate();
          return;
        }
        loading.value.reset = true;
        try {
          const res = await AgentService.resetApiKey(resolvedAgentId.value);
          apiKey.value = res.apiKey;
          apiKeyEnabled.value = Boolean(res.apiKeyEnabled);
          masked.value = false;
          canCopy.value = true;
          ElMessage.success('已重置 API Key');
        } catch (e) {
          ElMessage.error('重置失败');
        } finally {
          loading.value.reset = false;
        }
      };

      const handleDelete = async () => {
        if (!apiKey.value) return;
        try {
          await ElMessageBox.confirm('确认删除当前 API Key？删除后需重新生成。', '提示', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning',
          });
        } catch (e) {
          return;
        }

        loading.value.delete = true;
        try {
          const res = await AgentService.deleteApiKey(resolvedAgentId.value);
          apiKey.value = res.apiKey;
          apiKeyEnabled.value = Boolean(res.apiKeyEnabled);
          masked.value = true;
          canCopy.value = false;
          ElMessage.success('已删除 API Key');
        } catch (e) {
          ElMessage.error('删除失败');
        } finally {
          loading.value.delete = false;
        }
      };

      const handleCopy = async () => {
        if (!canCopy.value || !apiKey.value) {
          ElMessage.info('请重新生成或重置后复制完整 Key');
          return;
        }
        try {
          await navigator.clipboard.writeText(apiKey.value);
          ElMessage.success('已复制到剪贴板');
        } catch (e) {
          ElMessage.error('复制失败');
        }
      };

      const toggleMask = () => {
        if (!apiKey.value) return;
        masked.value = !masked.value;
      };

      const handleToggle = async (val: boolean) => {
        loading.value.toggle = true;
        try {
          const res = await AgentService.toggleApiKey(resolvedAgentId.value, val);
          apiKeyEnabled.value = Boolean(res.apiKeyEnabled);
          // 返回值可能是掩码
          apiKey.value = res.apiKey;
          masked.value = true;
          canCopy.value = false;
          ElMessage.success(val ? '已启用 API Key' : '已禁用 API Key');
        } catch (e) {
          apiKeyEnabled.value = !val;
          ElMessage.error('切换失败');
        } finally {
          loading.value.toggle = false;
        }
      };

      onMounted(() => {
        loadApiKey();
      });

      return {
        apiKey,
        apiKeyEnabled,
        masked,
        canCopy,
        loading,
        exampleTab,
        displayKey,
        curlExample,
        jsExample,
        pyExample,
        handleGenerate,
        handleReset,
        handleDelete,
        handleCopy,
        handleToggle,
        toggleMask,
      };
    },
  });
</script>

<style scoped>
  .access-api {
    max-width: 920px;
    margin: 0 auto;
    padding: 20px 10px;
  }

  .section {
    margin-bottom: 28px;
  }

  .desc {
    color: #666;
    margin: 4px 0 12px;
  }

  .card {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 16px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
  }

  .row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
  }

  .label {
    width: 110px;
    color: #333;
    font-weight: 600;
  }

  .key-row {
    flex-wrap: wrap;
  }

  .key-input {
    flex: 1;
    min-width: 240px;
  }

  .code {
    background: #0b1021;
    color: #e0e6f6;
    padding: 14px;
    border-radius: 8px;
    overflow: auto;
    font-size: 12px;
  }

  .tag {
    margin-left: 6px;
  }
</style>
