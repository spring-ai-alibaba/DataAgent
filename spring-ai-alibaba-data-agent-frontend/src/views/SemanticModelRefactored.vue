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
    <div class="semantic-model-page">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">
          <i class="bi bi-brain"></i>
          语义模型配置
        </h1>
        <p class="page-description">
          配置和管理数据字段的语义映射，提升AI理解能力
        </p>
      </div>

      <!-- 控制面板 -->
      <div class="control-panel">
        <div class="panel-left">
          <div class="search-section">
            <SearchBox
              v-model="searchKeyword"
              placeholder="搜索字段名称、描述或同义词..."
              @search="handleSearch"
            />
          </div>
          <div class="filter-section">
            <select v-model="selectedDataset" class="filter-select">
              <option value="">所有数据集</option>
              <option v-for="dataset in datasetList" :key="dataset.id" :value="dataset.id">
                {{ dataset.name }}
              </option>
            </select>
            <select v-model="selectedFieldType" class="filter-select">
              <option value="">所有字段类型</option>
              <option value="VARCHAR">文本</option>
              <option value="INTEGER">整数</option>
              <option value="DECIMAL">小数</option>
              <option value="DATE">日期</option>
              <option value="BOOLEAN">布尔</option>
            </select>
          </div>
        </div>
        <div class="panel-right">
          <button class="btn btn-primary" @click="showAddDialog = true">
            <i class="bi bi-plus-circle"></i>
            添加字段映射
          </button>
          <button class="btn btn-secondary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
            刷新
          </button>
        </div>
      </div>

      <!-- 数据表格 -->
      <div class="data-section">
        <DataTable
          :data="filteredSemanticModels"
          :columns="tableColumns"
          :loading="loading"
          @row-click="handleRowClick"
          @edit="handleEdit"
          @delete="handleDelete"
        />
      </div>

      <!-- 添加/编辑对话框 -->
      <div v-if="showAddDialog || showEditDialog" class="modal-overlay" @click="closeDialog">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h3>{{ showAddDialog ? '添加字段映射' : '编辑字段映射' }}</h3>
            <button class="btn-close" @click="closeDialog">
              <i class="bi bi-x"></i>
            </button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="saveSemanticModel">
              <div class="form-group">
                <label>数据集</label>
                <select v-model="formData.datasetId" required>
                  <option value="">请选择数据集</option>
                  <option v-for="dataset in datasetList" :key="dataset.id" :value="dataset.id">
                    {{ dataset.name }}
                  </option>
                </select>
              </div>
              <div class="form-group">
                <label>原始字段名</label>
                <input v-model="formData.originalFieldName" type="text" required />
              </div>
              <div class="form-group">
                <label>智能体字段名</label>
                <input v-model="formData.agentFieldName" type="text" required />
              </div>
              <div class="form-group">
                <label>字段同义词</label>
                <input v-model="formData.fieldSynonyms" type="text" placeholder="用逗号分隔多个同义词" />
              </div>
              <div class="form-group">
                <label>字段描述</label>
                <textarea v-model="formData.fieldDescription" rows="3"></textarea>
              </div>
              <div class="form-group">
                <label>字段类型</label>
                <select v-model="formData.fieldType" required>
                  <option value="VARCHAR">文本</option>
                  <option value="INTEGER">整数</option>
                  <option value="DECIMAL">小数</option>
                  <option value="DATE">日期</option>
                  <option value="BOOLEAN">布尔</option>
                </select>
              </div>
              <div class="form-group">
                <label>原始描述</label>
                <textarea v-model="formData.originalDescription" rows="2"></textarea>
              </div>
              <div class="form-group">
                <label class="checkbox-label">
                  <input v-model="formData.defaultRecall" type="checkbox" />
                  默认召回
                </label>
              </div>
              <div class="form-group">
                <label class="checkbox-label">
                  <input v-model="formData.enabled" type="checkbox" />
                  启用
                </label>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeDialog">
              取消
            </button>
            <button type="button" class="btn btn-primary" @click="saveSemanticModel">
              {{ showAddDialog ? '添加' : '保存' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 加载状态 -->
      <LoadingSpinner v-if="loading" />

      <!-- 消息提示 -->
      <MessageToast
        v-if="showToast"
        :message="toastMessage"
        :type="toastType"
        @close="showToast = false"
      />
    </div>
  </BaseLayout>
</template>

<script>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import BaseLayout from '../layouts/BaseLayout.vue'
import SearchBox from '../components/common/SearchBox.vue'
import DataTable from '../components/common/DataTable.vue'
import LoadingSpinner from '../components/common/LoadingSpinner.vue'
import MessageToast from '../components/common/MessageToast.vue'
import { semanticModelApi } from '../services/api.js'

export default {
  name: 'SemanticModel',
  components: {
    BaseLayout,
    SearchBox,
    DataTable,
    LoadingSpinner,
    MessageToast
  },
  setup() {
    const router = useRouter()
    
    // 响应式数据
    const loading = ref(false)
    const searchKeyword = ref('')
    const selectedDataset = ref('')
    const selectedFieldType = ref('')
    const showAddDialog = ref(false)
    const showEditDialog = ref(false)
    const showToast = ref(false)
    const toastMessage = ref('')
    const toastType = ref('success')
    
    // 表单数据
    const formData = reactive({
      id: null,
      datasetId: '',
      originalFieldName: '',
      agentFieldName: '',
      fieldSynonyms: '',
      fieldDescription: '',
      fieldType: 'VARCHAR',
      originalDescription: '',
      defaultRecall: true,
      enabled: true
    })
    
    // 数据列表
    const semanticModelList = ref([])
    const datasetList = ref([])
    
    // 表格列配置
    const tableColumns = [
      { key: 'agentFieldName', title: '智能体字段名', width: '150px' },
      { key: 'originalFieldName', title: '原始字段名', width: '150px' },
      { key: 'fieldType', title: '字段类型', width: '100px' },
      { key: 'fieldDescription', title: '字段描述', width: '200px' },
      { key: 'fieldSynonyms', title: '同义词', width: '150px' },
      { key: 'enabled', title: '状态', width: '80px', type: 'status' },
      { key: 'actions', title: '操作', width: '120px', type: 'actions' }
    ]
    
    // 计算属性
    const filteredSemanticModels = computed(() => {
      let filtered = semanticModelList.value
      
      if (searchKeyword.value) {
        const keyword = searchKeyword.value.toLowerCase()
        filtered = filtered.filter(item => 
          item.agentFieldName.toLowerCase().includes(keyword) ||
          item.originalFieldName.toLowerCase().includes(keyword) ||
          item.fieldDescription.toLowerCase().includes(keyword) ||
          item.fieldSynonyms.toLowerCase().includes(keyword)
        )
      }
      
      if (selectedDataset.value) {
        filtered = filtered.filter(item => item.datasetId === selectedDataset.value)
      }
      
      if (selectedFieldType.value) {
        filtered = filtered.filter(item => item.fieldType === selectedFieldType.value)
      }
      
      return filtered
    })
    
    // 方法
    const loadSemanticModels = async () => {
      loading.value = true
      try {
        const response = await semanticModelApi.getSemanticModels()
        semanticModelList.value = response.data || []
      } catch (error) {
        console.error('加载语义模型失败:', error)
        showToastMessage('加载数据失败', 'error')
      } finally {
        loading.value = false
      }
    }
    
    const loadDatasets = async () => {
      try {
        const response = await semanticModelApi.getDatasets()
        datasetList.value = response.data || []
      } catch (error) {
        console.error('加载数据集失败:', error)
      }
    }
    
    const handleSearch = () => {
      // 搜索逻辑已在计算属性中处理
    }
    
    const handleRowClick = (row) => {
      console.log('点击行:', row)
    }
    
    const handleEdit = (row) => {
      Object.assign(formData, row)
      showEditDialog.value = true
    }
    
    const handleDelete = async (row) => {
      if (confirm('确定要删除这个字段映射吗？')) {
        try {
          await semanticModelApi.deleteSemanticModel(row.id)
          await loadSemanticModels()
          showToastMessage('删除成功', 'success')
        } catch (error) {
          console.error('删除失败:', error)
          showToastMessage('删除失败', 'error')
        }
      }
    }
    
    const saveSemanticModel = async () => {
      try {
        if (showAddDialog.value) {
          await semanticModelApi.createSemanticModel(formData)
          showToastMessage('添加成功', 'success')
        } else {
          await semanticModelApi.updateSemanticModel(formData.id, formData)
          showToastMessage('更新成功', 'success')
        }
        await loadSemanticModels()
        closeDialog()
      } catch (error) {
        console.error('保存失败:', error)
        showToastMessage('保存失败', 'error')
      }
    }
    
    const closeDialog = () => {
      showAddDialog.value = false
      showEditDialog.value = false
      resetFormData()
    }
    
    const resetFormData = () => {
      Object.assign(formData, {
        id: null,
        datasetId: '',
        originalFieldName: '',
        agentFieldName: '',
        fieldSynonyms: '',
        fieldDescription: '',
        fieldType: 'VARCHAR',
        originalDescription: '',
        defaultRecall: true,
        enabled: true
      })
    }
    
    const refreshData = () => {
      loadSemanticModels()
      loadDatasets()
    }
    
    const showToastMessage = (message, type = 'success') => {
      toastMessage.value = message
      toastType.value = type
      showToast.value = true
    }
    
    // 生命周期
    onMounted(() => {
      loadSemanticModels()
      loadDatasets()
    })
    
    return {
      loading,
      searchKeyword,
      selectedDataset,
      selectedFieldType,
      showAddDialog,
      showEditDialog,
      showToast,
      toastMessage,
      toastType,
      formData,
      semanticModelList,
      datasetList,
      tableColumns,
      filteredSemanticModels,
      handleSearch,
      handleRowClick,
      handleEdit,
      handleDelete,
      saveSemanticModel,
      closeDialog,
      refreshData
    }
  }
}
</script>

<style scoped>
.semantic-model-page {
  min-height: 100vh;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

.page-header {
  background: white;
  border-bottom: 1px solid #e5e7eb;
  padding: 2rem;
  text-align: center;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

.page-title i {
  color: #3b82f6;
  font-size: 1.75rem;
}

.page-description {
  color: #6b7280;
  font-size: 1rem;
  margin: 0;
  line-height: 1.5;
}

.page-content {
  padding: 2rem;
  max-width: 1400px;
  margin: 0 auto;
}

.control-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding: 1.5rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
}

.panel-left {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.search-section {
  min-width: 300px;
}

.search-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  transition: all 0.2s ease;
}

.search-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}

.btn-outline {
  background: white;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.btn-outline:hover {
  background: #f9fafb;
  border-color: #d1d5db;
}

.btn-success {
  background: #10b981;
  color: white;
}

.btn-success:hover {
  background: #059669;
}

.btn-danger {
  background: #ef4444;
  color: white;
}

.btn-danger:hover {
  background: #dc2626;
}

.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.model-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  overflow: hidden;
  transition: all 0.2s ease;
}

.model-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.card-header {
  padding: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
}

.card-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card-title i {
  color: #3b82f6;
}

.card-subtitle {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

.card-body {
  padding: 1.5rem;
}

.model-info {
  margin-bottom: 1rem;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f3f4f6;
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  font-weight: 500;
  color: #374151;
  font-size: 0.875rem;
}

.info-value {
  color: #6b7280;
  font-size: 0.875rem;
}

.status-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-badge.active {
  background: #d1fae5;
  color: #065f46;
}

.status-badge.inactive {
  background: #fee2e2;
  color: #991b1b;
}

.card-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #f3f4f6;
}

.action-btn {
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.action-btn.edit {
  background: #dbeafe;
  color: #1e40af;
}

.action-btn.edit:hover {
  background: #bfdbfe;
}

.action-btn.delete {
  background: #fee2e2;
  color: #991b1b;
}

.action-btn.delete:hover {
  background: #fecaca;
}

.action-btn.test {
  background: #d1fae5;
  color: #065f46;
}

.action-btn.test:hover {
  background: #a7f3d0;
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #6b7280;
  cursor: pointer;
  padding: 0.25rem;
}

.close-btn:hover {
  color: #374151;
}

.modal-body {
  padding: 1.5rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1.5rem;
  border-top: 1px solid #e5e7eb;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.form-control {
  width: 100%;
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

.form-control:disabled {
  background: #f9fafb;
  color: #9ca3af;
  cursor: not-allowed;
}

textarea.form-control {
  resize: vertical;
  min-height: 100px;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 3rem 2rem;
  color: #6b7280;
}

.empty-icon {
  font-size: 4rem;
  color: #d1d5db;
  margin-bottom: 1rem;
}

.empty-text {
  font-size: 1.1rem;
  margin-bottom: 1rem;
}

.empty-action {
  margin-top: 1.5rem;
}

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: #6b7280;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #e5e7eb;
  border-top: 2px solid #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-right: 0.75rem;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-content {
    padding: 1rem;
  }
  
  .control-panel {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }
  
  .panel-left {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-section {
    min-width: auto;
  }
  
  .model-grid {
    grid-template-columns: 1fr;
  }
  
  .card-actions {
    flex-direction: column;
  }
  
  .modal-content {
    width: 95%;
    margin: 1rem;
  }
}

.filter-section {
  display: flex;
  gap: 12px;
}

.filter-select {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
}

.panel-right {
  display: flex;
  gap: 12px;
}

.data-section {
  background: var(--color-bg-card);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--color-bg-card);
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  margin: 0;
  color: var(--color-text);
}

.btn-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 4px;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 500;
  color: var(--color-text);
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: auto;
  margin: 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--color-border);
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-primary {
  background: var(--color-primary);
  color: white;
}

.btn-primary:hover {
  background: var(--color-primary-dark);
}

.btn-secondary {
  background: var(--color-bg-secondary);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}

.btn-secondary:hover {
  background: var(--color-bg-hover);
}

@media (max-width: 768px) {
  .semantic-model-page {
    padding: 16px;
  }
  
  .control-panel {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
  
  .panel-left {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-section {
    min-width: auto;
  }
  
  .filter-section {
    flex-direction: column;
  }
  
  .panel-right {
    justify-content: center;
  }
}
</style>
