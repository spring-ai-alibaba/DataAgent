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
    <div class="business-knowledge-page">
      <div class="page-header">
        <div class="header-content">
          <h1 class="page-title">
            <i class="bi bi-book"></i>
            业务知识管理
          </h1>
          <p class="page-subtitle">管理企业知识引擎，配置业务术语、黑话和常用表达</p>
        </div>
        <div class="header-actions">
          <button class="btn btn-outline" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
            刷新
          </button>
          <button class="btn btn-primary" @click="openCreateModal">
            <i class="bi bi-plus-lg"></i>
            新增知识
          </button>
        </div>
      </div>

      <div class="page-content">
        <!-- 搜索和筛选 -->
        <div class="search-section">
          <div class="search-container">
            <SearchBox
              v-model="searchKeyword"
              placeholder="搜索知识内容..."
              @search="handleSearch"
              @clear="handleSearchClear"
            />
            <div class="filter-controls">
              <select v-model="selectedDataset" class="form-control" @change="handleDatasetChange">
                <option value="">所有数据集</option>
                <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">
                  {{ dataset.name }}
                </option>
              </select>
              <select v-model="selectedType" class="form-control" @change="handleTypeChange">
                <option value="">所有类型</option>
                <option value="term">业务术语</option>
                <option value="synonym">同义词</option>
                <option value="expression">常用表达</option>
              </select>
            </div>
          </div>
        </div>

        <!-- 数据表格 -->
        <div class="table-section">
          <DataTable
            :data="filteredData"
            :columns="tableColumns"
            :loading="loading"
            :selectable="true"
            :refreshable="true"
            @selection-change="handleSelectionChange"
            @refresh="refreshData"
            @row-click="handleRowClick"
          >
            <template #type="{ value }">
              <span class="badge" :class="getTypeBadgeClass(value)">
                {{ getTypeText(value) }}
              </span>
            </template>
            <template #actions="{ row }">
              <div class="action-buttons">
                <button class="btn btn-sm btn-outline" @click="editItem(row)">
                  <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-error" @click="deleteItem(row)">
                  <i class="bi bi-trash"></i>
                </button>
              </div>
            </template>
            <template #empty>
              <div class="empty-state">
                <i class="bi bi-book"></i>
                <p>暂无业务知识数据</p>
                <button class="btn btn-primary" @click="openCreateModal">
                  <i class="bi bi-plus-lg"></i>
                  创建第一条知识
                </button>
              </div>
            </template>
          </DataTable>
        </div>

        <!-- 批量操作 -->
        <div v-if="selectedRows.length > 0" class="batch-actions">
          <div class="batch-info">
            已选择 {{ selectedRows.length }} 项
          </div>
          <div class="batch-buttons">
            <button class="btn btn-outline" @click="batchDelete">
              <i class="bi bi-trash"></i>
              批量删除
            </button>
            <button class="btn btn-outline" @click="batchExport">
              <i class="bi bi-download"></i>
              批量导出
            </button>
          </div>
        </div>
      </div>

      <!-- 创建/编辑模态框 -->
      <div v-if="showModal" class="modal-overlay" @click="closeModal">
        <div class="modal-dialog" @click.stop>
          <div class="modal-header">
            <h3>{{ isEditing ? '编辑知识' : '新增知识' }}</h3>
            <button class="close-btn" @click="closeModal">
              <i class="bi bi-x"></i>
            </button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="saveItem">
              <div class="form-group">
                <label class="form-label required">知识类型</label>
                <select v-model="formData.type" class="form-control" required>
                  <option value="">请选择类型</option>
                  <option value="term">业务术语</option>
                  <option value="synonym">同义词</option>
                  <option value="expression">常用表达</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label required">关键词</label>
                <input
                  v-model="formData.keyword"
                  type="text"
                  class="form-control"
                  placeholder="请输入关键词"
                  required
                />
              </div>
              <div class="form-group">
                <label class="form-label">同义词/表达</label>
                <textarea
                  v-model="formData.synonyms"
                  class="form-control"
                  rows="3"
                  placeholder="请输入同义词或常用表达，多个用逗号分隔"
                ></textarea>
              </div>
              <div class="form-group">
                <label class="form-label">描述</label>
                <textarea
                  v-model="formData.description"
                  class="form-control"
                  rows="3"
                  placeholder="请输入描述信息"
                ></textarea>
              </div>
              <div class="form-group">
                <label class="form-label">数据集</label>
                <select v-model="formData.datasetId" class="form-control">
                  <option value="">请选择数据集</option>
                  <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">
                    {{ dataset.name }}
                  </option>
                </select>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline" @click="closeModal">取消</button>
            <button type="button" class="btn btn-primary" @click="saveItem" :disabled="saving">
              <i class="bi bi-check-lg" v-if="!saving"></i>
              <div class="spinner" v-if="saving"></div>
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </BaseLayout>
</template>

<script>
import { ref, reactive, computed, onMounted } from 'vue'
import BaseLayout from '@/layouts/BaseLayout.vue'
import SearchBox from '@/components/common/SearchBox.vue'
import DataTable from '@/components/common/DataTable.vue'
import { businessKnowledgeApi } from '@/services/api.js'
import { debounce } from '@/utils/common.js'
import toast from '@/utils/toast.js'

export default {
  name: 'BusinessKnowledge',
  components: {
    BaseLayout,
    SearchBox,
    DataTable
  },
  setup() {
    // 响应式数据
    const loading = ref(false)
    const data = ref([])
    const datasets = ref([])
    const searchKeyword = ref('')
    const selectedDataset = ref('')
    const selectedType = ref('')
    const selectedRows = ref([])
    const showModal = ref(false)
    const isEditing = ref(false)
    const saving = ref(false)
    const editingItem = ref(null)

    // 表单数据
    const formData = reactive({
      type: '',
      keyword: '',
      synonyms: '',
      description: '',
      datasetId: ''
    })

    // 表格列配置
    const tableColumns = [
      { key: 'keyword', title: '关键词', sortable: true },
      { key: 'type', title: '类型', sortable: true },
      { key: 'synonyms', title: '同义词/表达', sortable: false },
      { key: 'description', title: '描述', sortable: false },
      { key: 'datasetName', title: '数据集', sortable: true },
      { key: 'actions', title: '操作', sortable: false, width: '120px' }
    ]

    // 计算属性
    const filteredData = computed(() => {
      let result = data.value

      // 关键词搜索
      if (searchKeyword.value) {
        const keyword = searchKeyword.value.toLowerCase()
        result = result.filter(item =>
          item.keyword.toLowerCase().includes(keyword) ||
          item.synonyms.toLowerCase().includes(keyword) ||
          item.description.toLowerCase().includes(keyword)
        )
      }

      // 数据集筛选
      if (selectedDataset.value) {
        result = result.filter(item => item.datasetId === selectedDataset.value)
      }

      // 类型筛选
      if (selectedType.value) {
        result = result.filter(item => item.type === selectedType.value)
      }

      return result
    })

    // 获取类型文本
    const getTypeText = (type) => {
      const typeMap = {
        term: '业务术语',
        synonym: '同义词',
        expression: '常用表达'
      }
      return typeMap[type] || type
    }

    // 获取类型徽章样式
    const getTypeBadgeClass = (type) => {
      const classMap = {
        term: 'badge-primary',
        synonym: 'badge-success',
        expression: 'badge-info'
      }
      return classMap[type] || 'badge-secondary'
    }

    // 加载数据
    const loadData = async () => {
      try {
        loading.value = true
        const result = await businessKnowledgeApi.getList()
        if (result.success) {
          data.value = result.data || []
        } else {
          toast.error('加载数据失败: ' + result.message)
        }
      } catch (error) {
        console.error('加载数据失败:', error)
        toast.error('加载数据失败，请检查网络连接')
      } finally {
        loading.value = false
      }
    }

    // 加载数据集
    const loadDatasets = async () => {
      try {
        const result = await businessKnowledgeApi.getDatasetIds()
        if (result.success) {
          datasets.value = result.data || []
        }
      } catch (error) {
        console.error('加载数据集失败:', error)
      }
    }

    // 刷新数据
    const refreshData = () => {
      loadData()
      loadDatasets()
    }

    // 搜索处理
    const handleSearch = debounce(() => {
      // 搜索逻辑已在计算属性中处理
    }, 300)

    // 清空搜索
    const handleSearchClear = () => {
      searchKeyword.value = ''
    }

    // 数据集变更
    const handleDatasetChange = () => {
      // 筛选逻辑已在计算属性中处理
    }

    // 类型变更
    const handleTypeChange = () => {
      // 筛选逻辑已在计算属性中处理
    }

    // 选择变更
    const handleSelectionChange = (rows) => {
      selectedRows.value = rows
    }

    // 行点击
    const handleRowClick = (row) => {
      editItem(row)
    }

    // 打开创建模态框
    const openCreateModal = () => {
      isEditing.value = false
      editingItem.value = null
      resetForm()
      showModal.value = true
    }

    // 编辑项目
    const editItem = (item) => {
      isEditing.value = true
      editingItem.value = item
      formData.type = item.type
      formData.keyword = item.keyword
      formData.synonyms = item.synonyms
      formData.description = item.description
      formData.datasetId = item.datasetId
      showModal.value = true
    }

    // 删除项目
    const deleteItem = async (item) => {
      if (!confirm('确定要删除这条知识吗？')) return

      try {
        const result = await businessKnowledgeApi.delete(item.id)
        if (result.success) {
          toast.success('删除成功')
          loadData()
        } else {
          toast.error('删除失败: ' + result.message)
        }
      } catch (error) {
        console.error('删除失败:', error)
        toast.error('删除失败，请检查网络连接')
      }
    }

    // 批量删除
    const batchDelete = async () => {
      if (!confirm(`确定要删除选中的 ${selectedRows.value.length} 条知识吗？`)) return

      try {
        const promises = selectedRows.value.map(item => businessKnowledgeApi.delete(item.id))
        await Promise.all(promises)
        toast.success('批量删除成功')
        selectedRows.value = []
        loadData()
      } catch (error) {
        console.error('批量删除失败:', error)
        toast.error('批量删除失败')
      }
    }

    // 批量导出
    const batchExport = () => {
      const exportData = selectedRows.value.map(item => ({
        关键词: item.keyword,
        类型: getTypeText(item.type),
        同义词: item.synonyms,
        描述: item.description,
        数据集: item.datasetName
      }))

      const csvContent = [
        Object.keys(exportData[0]).join(','),
        ...exportData.map(row => Object.values(row).join(','))
      ].join('\n')

      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `业务知识_${new Date().toISOString().split('T')[0]}.csv`
      link.click()
    }

    // 关闭模态框
    const closeModal = () => {
      showModal.value = false
      resetForm()
    }

    // 重置表单
    const resetForm = () => {
      formData.type = ''
      formData.keyword = ''
      formData.synonyms = ''
      formData.description = ''
      formData.datasetId = ''
    }

    // 保存项目
    const saveItem = async () => {
      try {
        saving.value = true

        const payload = {
          type: formData.type,
          keyword: formData.keyword,
          synonyms: formData.synonyms,
          description: formData.description,
          datasetId: formData.datasetId
        }

        let result
        if (isEditing.value) {
          result = await businessKnowledgeApi.update(editingItem.value.id, payload)
        } else {
          result = await businessKnowledgeApi.create(payload)
        }

        if (result.success) {
          toast.success(isEditing.value ? '更新成功' : '创建成功')
          closeModal()
          loadData()
        } else {
          toast.error((isEditing.value ? '更新失败: ' : '创建失败: ') + result.message)
        }
      } catch (error) {
        console.error('保存失败:', error)
        toast.error('保存失败，请检查网络连接')
      } finally {
        saving.value = false
      }
    }

    // 初始化
    onMounted(() => {
      loadData()
      loadDatasets()
    })

    return {
      loading,
      data,
      datasets,
      searchKeyword,
      selectedDataset,
      selectedType,
      selectedRows,
      showModal,
      isEditing,
      saving,
      formData,
      tableColumns,
      filteredData,
      getTypeText,
      getTypeBadgeClass,
      refreshData,
      handleSearch,
      handleSearchClear,
      handleDatasetChange,
      handleTypeChange,
      handleSelectionChange,
      handleRowClick,
      openCreateModal,
      editItem,
      deleteItem,
      batchDelete,
      batchExport,
      closeModal,
      saveItem
    }
  }
}
</script>

<style scoped>
.business-knowledge-page {
  min-height: 100vh;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

.page-header {
  background: white;
  border-bottom: 1px solid #e5e7eb;
  padding: 2rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page-title i {
  color: #3b82f6;
  font-size: 1.75rem;
}

.page-subtitle {
  color: #6b7280;
  margin: 0;
  font-size: 1rem;
  line-height: 1.5;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
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

.btn-outline {
  background: white;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.btn-outline:hover {
  background: #f9fafb;
  border-color: #d1d5db;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}

.page-content {
  padding: 2rem;
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.search-section {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
}

.search-container {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.search-container .search-box {
  flex: 1;
}

.filter-controls {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.form-control {
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  transition: all 0.2s ease;
  background: white;
}

.form-control:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.filter-controls .form-control {
  min-width: 150px;
}

.table-section {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  overflow: hidden;
}

.batch-actions {
  background: #dbeafe;
  border: 1px solid #3b82f6;
  border-radius: 12px;
  padding: 1rem 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.batch-info {
  color: #1e40af;
  font-weight: 500;
}

.batch-actions .btn {
  padding: 0.5rem 1rem;
  font-size: 0.75rem;
}

.btn-danger {
  background: #ef4444;
  color: white;
}

.btn-danger:hover {
  background: #dc2626;
}

.btn-secondary {
  background: #f9fafb;
  color: #374151;
  border: 1px solid #d1d5db;
}

.btn-secondary:hover {
  background: #f3f4f6;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.table th {
  background: #f9fafb;
  padding: 1rem;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}

.table td {
  padding: 1rem;
  border-bottom: 1px solid #f3f4f6;
  color: #6b7280;
}

.table tr:last-child td {
  border-bottom: none;
}

.table tr:hover {
  background: #f9fafb;
}

.checkbox {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
}

.action-btn {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
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
  .page-header {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }
  
  .header-actions {
    justify-content: flex-end;
  }
  
  .page-content {
    padding: 1rem;
  }
  
  .search-container {
    flex-direction: column;
    align-items: stretch;
  }
  
  .filter-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .table {
    font-size: 0.75rem;
  }
  
  .table th,
  .table td {
    padding: 0.75rem 0.5rem;
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 0.25rem;
  }
  
  .modal-content {
    width: 95%;
    margin: 1rem;
  }
}
</style>