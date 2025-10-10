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
    <div class="create-agent-page">

    <!-- 页面标题区域 -->
    <div class="title-section">
      <div class="container">
        <div class="title-content">
          <div class="title-icon">
            <i class="bi bi-plus-circle"></i>
          </div>
          <div class="title-info">
            <h1 class="page-title">创建新的智能体</h1>
            <p class="page-subtitle">配置您的专属数据分析智能体，让AI帮助您更好地理解和分析数据</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建表单 -->
    <div class="container">
      <div class="create-form-wrapper">
        <div class="create-form">
          <div class="form-section">
            <div class="section-title">
              <h3>基本信息</h3>
              <p>设置智能体的基本属性</p>
            </div>

            <div class="form-grid">
              <div class="form-group">
                <label for="agentName">智能体名称 *</label>
                <input 
                  type="text" 
                  id="agentName"
                  v-model="agentForm.name" 
                  placeholder="请输入智能体名称"
                  class="form-control"
                  required
                >
              </div>

              <div class="form-group">
                <label for="agentCategory">分类</label>
                <select id="agentCategory" v-model="agentForm.category" class="form-control">
                  <option value="">请选择分类</option>
                  <option value="数据分析">数据分析</option>
                  <option value="业务分析">业务分析</option>
                  <option value="财务分析">财务分析</option>
                  <option value="供应链">供应链</option>
                  <option value="营销">营销</option>
                  <option value="其他">其他</option>
                </select>
              </div>

              <div class="form-group full-width">
                <label for="agentDescription">智能体描述</label>
                <textarea 
                  id="agentDescription"
                  v-model="agentForm.description" 
                  placeholder="请输入智能体的功能描述和使用场景"
                  class="form-control"
                  rows="4"
                ></textarea>
              </div>

              <div class="form-group">
                <label>头像设置</label>
                <div class="avatar-upload">
                  <div class="avatar-preview">
                    <img :src="agentForm.avatar" alt="智能体头像">
                  </div>
                  <div class="avatar-controls">
                    <div class="avatar-buttons">
                      <button type="button" class="btn btn-outline" @click="regenerateAvatar">
                        <i class="bi bi-arrow-clockwise"></i>
                        重新生成
                      </button>
                      <button type="button" class="btn btn-outline" @click="triggerFileUpload" :disabled="uploading">
                        <i class="bi bi-upload" v-if="!uploading"></i>
                        <i class="bi bi-hourglass-split" v-if="uploading"></i>
                        {{ uploading ? '上传中...' : '上传图片' }}
                      </button>
                      <input 
                        ref="fileInput" 
                        type="file" 
                        accept="image/*" 
                        style="display: none" 
                        @change="handleFileUpload"
                      >
                    </div>
                  </div>
                </div>
              </div>

              <div class="form-group">
                <label for="agentTags">标签</label>
                <input 
                  type="text" 
                  id="agentTags"
                  v-model="agentForm.tags" 
                  placeholder="请输入标签，用逗号分隔"
                  class="form-control"
                >
                <div class="form-help">例如：数据分析,销售,业务指标</div>
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="section-title">
              <h3>智能体类型</h3>
              <p>选择适合您业务需求的智能体类型</p>
            </div>

            <div class="template-grid">
              <div class="template-card" @click="useTemplate('data-analyst')">
                <h5>数据分析师</h5>
                <p>专业的数据分析和SQL查询助手</p>
              </div>
              <div class="template-card" @click="useTemplate('report-generator')">
                <h5>报表生成器</h5>
                <p>自动生成各类业务报表</p>
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="section-title">
              <h3>发布设置</h3>
              <p>选择智能体的初始状态</p>
            </div>

            <div class="publish-options">
              <label class="radio-option">
                <input type="radio" value="draft" v-model="agentForm.status" checked>
                <span class="radio-label">
                  <strong>保存为草稿</strong>
                  <span class="radio-desc">暂不发布，可以继续编辑配置</span>
                </span>
              </label>
              <label class="radio-option">
                <input type="radio" value="published" v-model="agentForm.status">
                <span class="radio-label">
                  <strong>立即发布</strong>
                  <span class="radio-desc">创建完成后立即发布供用户使用</span>
                </span>
              </label>
            </div>
          </div>

          <!-- 表单操作按钮 -->
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="goBack">取消</button>
            <button type="button" class="btn btn-secondary" @click="saveDraft">保存草稿</button>
            <button type="button" class="btn btn-primary" @click="createAgent" :disabled="loading">
              <span v-if="loading">创建中...</span>
              <span v-else>创建智能体</span>
            </button>
          </div>
        </div>

        <!-- 预览面板 -->
        <div class="preview-panel">
          <div class="preview-header">
            <h3>预览</h3>
            <p>查看智能体的外观效果</p>
          </div>

          <div class="agent-preview">
            <div class="preview-avatar">
              <img :src="agentForm.avatar" :alt="agentForm.name || '智能体'">
            </div>
            <div class="preview-info">
              <h4>{{ agentForm.name || '智能体名称' }}</h4>
              <p>{{ agentForm.description || '智能体描述...' }}</p>
              <div class="preview-meta">
                <span class="preview-category">{{ agentForm.category || '未分类' }}</span>
                <span class="preview-status" :class="agentForm.status">
                  {{ agentForm.status === 'published' ? '已发布' : '草稿' }}
                </span>
              </div>
              <div class="preview-tags" v-if="agentForm.tags">
                <span v-for="tag in agentForm.tags.split(',')" :key="tag.trim()" class="tag">
                  {{ tag.trim() }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  </BaseLayout>
</template>

<script>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BaseLayout from '../layouts/BaseLayout.vue'
import { fileUploadApi } from '@/services/api'
import { generateRandomAvatar } from '@/utils/avatar'
import agentService from '@/services/agent'

export default {
  name: 'CreateAgent',
  components: {
    BaseLayout
  },
  setup() {
    const router = useRouter()
    const loading = ref(false)
    const fileInput = ref(null)
    const uploading = ref(false)

    const agentForm = reactive({
      name: '',
      description: '',
      avatar: '',
      category: '',
      tags: '',
      prompt: '',
      status: 'draft'
    })

    // 组件挂载时生成随机头像
    onMounted(() => {
      // 直接使用备用头像，确保能正常显示
      agentForm.avatar = generateFallbackAvatar()
      console.log('Generated fallback avatar:', agentForm.avatar)
    })

    // 备用头像生成函数
    const generateFallbackAvatar = () => {
      const colors = ['3B82F6', '8B5CF6', '10B981', 'F59E0B', 'EF4444', '6366F1', 'EC4899', '14B8A6']
      const randomColor = colors[Math.floor(Math.random() * colors.length)]
      const letters = ['AI', '数据', '智能', 'DA', 'BI', 'ML', 'DL', 'NL']
      const randomLetter = letters[Math.floor(Math.random() * letters.length)]
      
      // 使用最简单的 SVG，只有背景色和文字
      const svg = `<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg">
        <rect width="200" height="200" fill="#${randomColor}"/>
        <text x="100" y="120" font-family="Arial, sans-serif" font-size="48" font-weight="bold" text-anchor="middle" fill="white">${randomLetter}</text>
      </svg>`
      
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    }

    // Prompt模板
    const promptTemplates = {
      'data-analyst': `你是一个专业的数据分析助手，具备以下能力：

1. 根据用户的自然语言问题，生成准确的SQL查询语句
2. 分析数据结果并提供业务洞察
3. 解释查询逻辑和数据含义
4. 提供数据可视化建议

请始终保持专业、准确和友好的态度，确保查询结果的正确性和实用性。`,

      'business-advisor': `你是一个资深的业务顾问，专注于：

1. 解答各类业务问题和挑战
2. 提供战略建议和解决方案
3. 分析市场趋势和机会
4. 优化业务流程和效率

请以专业、客观的角度提供建议，确保信息的准确性和实用性。`,

      'report-generator': `你是一个专业的报表生成助手，擅长：

1. 根据数据生成各类业务报表
2. 创建数据汇总和分析报告
3. 提供图表和可视化建议
4. 解释数据趋势和异常

请确保报表格式清晰、数据准确、结论明确。`
    }

    const goBack = () => {
      router.push('/agents')
    }

    // 导航方法已移至BaseLayout组件

    const useTemplate = (templateKey) => {
      agentForm.prompt = promptTemplates[templateKey]
    }

    const saveDraft = async () => {
      agentForm.status = 'draft'
      await createAgent()
    }

    const regenerateAvatar = () => {
      // 直接使用备用头像生成函数
      agentForm.avatar = generateFallbackAvatar()
      console.log('Regenerated avatar:', agentForm.avatar)
    }

    // 触发文件选择
    const triggerFileUpload = () => {
      if (fileInput.value) {
        fileInput.value.click()
      }
    }

    // 处理文件上传
    const handleFileUpload = async (event) => {
      const file = event.target.files[0]
      if (!file) return

      // 验证文件类型
      if (!file.type.startsWith('image/')) {
        alert('请选择图片文件')
        return
      }

      // 验证文件大小 (5MB)
      if (file.size > 5 * 1024 * 1024) {
        alert('图片大小不能超过5MB')
        return
      }

      try {
        uploading.value = true
        
        // 保存当前头像，用于失败时恢复
        const originalAvatar = agentForm.avatar
        
        // 显示上传中的预览（使用base64）
        const reader = new FileReader()
        reader.onload = (e) => {
          agentForm.avatar = e.target.result
        }
        reader.readAsDataURL(file)

        // 上传文件
        const response = await fileUploadApi.uploadAvatar(file)
        
        if (response.success) {
          // 上传成功，使用服务器返回的URL
          agentForm.avatar = response.url
          console.log('头像上传成功:', response.url)
        } else {
          throw new Error(response.message || '上传失败')
        }
      } catch (error) {
        console.error('头像上传失败:', error)
        alert('头像上传失败: ' + error.message)
        // 恢复之前的头像
        agentForm.avatar = generateFallbackAvatar()
      } finally {
        uploading.value = false
        // 清空文件输入
        if (fileInput.value) {
          fileInput.value.value = ''
        }
      }
    }

    // 图片加载成功处理
    const handleImageLoad = (event) => {
      console.log('头像图片加载成功:', event.target.src)
    }

    // 图片加载失败处理
    const handleImageError = (event) => {
      console.error('头像图片加载失败:', event.target.src)
      console.error('错误详情:', event)
      // 可以在这里设置一个默认头像
      // agentForm.avatar = generateFallbackAvatar()
    }

    const createAgent = async () => {
      if (!agentForm.name.trim()) {
        alert('请填写智能体名称')
        return
      }

      try {
        loading.value = true

        const agentData = {
          name: agentForm.name.trim(),
          description: agentForm.description.trim(),
          avatar: agentForm.avatar.trim() || generateRandomAvatar(),
          category: agentForm.category.trim(),
          tags: agentForm.tags.trim(),
          prompt: agentForm.prompt.trim(),
          status: agentForm.status
        }

        const result = await agentService.create(agentData)
        
        alert(`智能体创建成功！状态：${agentData.status === 'published' ? '已发布' : '草稿'}`)
        await router.push(`/agent/${result.id}`)
      } catch (error) {
        console.error('创建智能体失败:', error)
        alert('创建失败，请重试')
      } finally {
        loading.value = false
      }
    }

    return {
      agentForm,
      loading,
      fileInput,
      uploading,
      goBack,
      // 导航方法已移至BaseLayout组件
      useTemplate,
      saveDraft,
      regenerateAvatar,
      generateFallbackAvatar,
      triggerFileUpload,
      handleFileUpload,
      handleImageLoad,
      handleImageError,
      createAgent
    }
  }
}
</script>

<style scoped>
.create-agent-page {
  min-height: 100vh;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* 头部导航样式已移至BaseLayout组件 */

/* 页面标题区域 */
.title-section {
  background: white;
  border-bottom: 1px solid #e5e7eb;
  padding: 2rem 0;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.title-content {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.title-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.75rem;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.title-info {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.page-subtitle {
  color: #6b7280;
  font-size: 1rem;
  margin: 0;
  line-height: 1.5;
}

/* 表单布局 */
.create-form-wrapper {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 2rem;
  margin-top: 2rem;
  margin-bottom: 2rem;
}

.create-form {
  background: white;
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
}

.form-section {
  margin-bottom: 2rem;
  padding-bottom: 2rem;
  border-bottom: 1px solid #f3f4f6;
}

.form-section:last-of-type {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.section-title {
  margin-bottom: 1.5rem;
}

.section-title h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.section-title p {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

.form-group.full-width {
  grid-column: 1 / -1;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group label {
  font-weight: 500;
  color: #374151;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
}

.form-control {
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  transition: all 0.2s ease;
}

.form-control:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-help {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 0.25rem;
}

/* 头像上传 */
.avatar-upload {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #e5e7eb;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-controls {
  flex: 1;
}

.avatar-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

/* 模板选择 */
.template-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.template-card {
  padding: 1rem;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.template-card:hover {
  border-color: #3b82f6;
  background: #f8fafc;
}

.template-card h5 {
  margin: 0 0 0.5rem 0;
  font-size: 0.875rem;
  font-weight: 600;
  color: #1f2937;
}

.template-card p {
  margin: 0;
  font-size: 0.75rem;
  color: #6b7280;
}

/* 发布选项 */
.publish-options {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.radio-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  cursor: pointer;
}

.radio-option input[type="radio"] {
  margin: 0;
}

.radio-label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.radio-label strong {
  font-size: 0.875rem;
  color: #1f2937;
}

.radio-desc {
  font-size: 0.75rem;
  color: #6b7280;
}

/* 表单操作 */
.form-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  margin-top: 2rem;
  padding-top: 2rem;
  border-top: 1px solid #f3f4f6;
}

.btn-secondary {
  background: #f9fafb;
  color: #374151;
  border: 1px solid #d1d5db;
}

.btn-secondary:hover {
  background: #f3f4f6;
}

/* 预览面板 */
.preview-panel {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  height: fit-content;
}

.preview-header {
  margin-bottom: 1.5rem;
}

.preview-header h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.preview-header p {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

.agent-preview {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.preview-avatar {
  width: 100%;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #e5e7eb;
}

.preview-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-info h4 {
  font-size: 1rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.preview-info p {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0 0 1rem 0;
  line-height: 1.5;
}

.preview-meta {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.preview-category {
  padding: 0.25rem 0.5rem;
  background: #f3f4f6;
  color: #6b7280;
  border-radius: 4px;
  font-size: 0.75rem;
}

.preview-status {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
}

.preview-status.published {
  background: #d1fae5;
  color: #065f46;
}

.preview-status.draft {
  background: #fef3c7;
  color: #92400e;
}

.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.tag {
  padding: 0.25rem 0.5rem;
  background: #dbeafe;
  color: #1e40af;
  border-radius: 4px;
  font-size: 0.75rem;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .create-form-wrapper {
    grid-template-columns: 1fr;
  }
  
  .preview-panel {
    order: -1;
  }
}

@media (max-width: 768px) {
  .container {
    padding: 0 1rem;
  }
  
  .title-content {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .form-grid {
    grid-template-columns: 1fr;
  }
  
  .template-grid {
    grid-template-columns: 1fr;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .avatar-upload {
    flex-direction: column;
    align-items: center;
  }
  
  .avatar-buttons {
    justify-content: center;
  }
}
</style>
