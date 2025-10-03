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
  <div style="padding: 20px">
    <div style="margin-bottom: 20px;">
      <h2>数据源配置</h2>
    </div>
    <el-divider/>

    <div style="margin-bottom: 30px;">
      <el-row style="display: flex; justify-content: space-between; align-items: center;">
        <el-col :span="12">
          <h3>数据源列表</h3>
        </el-col>
        <el-col :span="12" style="text-align: right;">
          <el-button @click="dialogVisible = true" size="large" type="primary" round :icon="Plus">添加数据源</el-button>
          <el-button @click="initAgentDatasource" v-if="!initStatus" size="large" type="primary" round :icon="UploadFilled">初始化数据源</el-button>
          <el-button v-else size="large" type="primary" round loading>初始化中...</el-button>
        </el-col>
      </el-row>
    </div>

    <el-table :data="datasource" style="width: 100%" border>
      <el-table-column prop="name" label="数据源名称" min-width="120px"/>
      <el-table-column prop="type" label="数据源类型" min-width="100px"/>
      <el-table-column prop="connectionUrl" label="连接地址" min-width="200px"/>
      <el-table-column label="连接状态" min-width="50px">
        <template #default="scope">
          <el-tag :type="scope.row.testStatus === 'success' ? 'success' : 'danger'" round>
            {{ scope.row.testStatus === 'success' ? '连接成功' : '连接失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="40px">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" round>
            {{ scope.row.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="100px"/>
      <el-table-column label="操作" min-width="120px">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'active'" @click="changeDatasource(scope.row, false)" size="small" type="warning" round plain>禁用</el-button>
          <el-button v-else @click="changeDatasource(scope.row, true)" size="small" type="success" round plain>启用</el-button>
          <el-button @click="testConnection(scope.row)" size="small" type="primary" round plain>测试连接</el-button>
          <el-button @click="removeAgentDatasource(scope.row)" size="small" type="danger" round plain>移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 添加数据源Dialog -->
  <el-dialog v-model="dialogVisible" title="添加数据源" width="1000">
    <el-tabs v-model="dialogActiveName" type="card" stretch>
      <el-tab-pane label="选择已有数据源" name="select">
        <!-- todo: 添加分页和查询 -->
        <el-table @current-change="handleSelectDatasourceChange" :data="allDatasource" highlight-current-row style="width: 100%">
          <el-table-column property="name" label="数据源名称" width="200"/>
          <el-table-column property="type" label="数据源类型" width="100"/>
          <el-table-column property="host" label="Host" width="100"/>
          <el-table-column property="port" label="Port" width="100"/>
          <el-table-column property="description" label="描述" width="400"/>
        </el-table>
        <el-divider/>
        <div style="text-align: right;">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="addSelectDatasource">
            添加选中数据源
          </el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="添加新数据源" name="add">

        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>数据源名称 *</label>
              <el-input v-model="newDatasource.name" placeholder="请输入数据源名称" size="large"/>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>数据源类型 *</label>
              <!-- todo: 改为后端动态获取-->
              <el-select v-model="newDatasource.type" placeholder="请选择数据源类型" style="width: 100%" size="large">
                <el-option key="mysql" label="MySQL" value="mysql"/>
                <el-option key="postgresql" label="PostgreSQL" value="postgresql"/>
              </el-select>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>主机地址 *</label>
              <el-input v-model="newDatasource.host" placeholder="例如：localhost 或 192.168.1.100" size="large"/>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>端口号 *</label>
              <el-input-number v-model="newDatasource.port" :min="0" :max="65535" size="large" style="width: 100%"/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col>
            <div class="form-item">
              <label>数据库名 *</label>
              <el-input v-model="newDatasource.databaseName" placeholder="请输入数据库名称" size="large"/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col>
            <div class="form-item">
              <label>连接地址</label>
              <el-input v-model="newDatasource.connectionUrl" placeholder="请输入JDBC地址（若不填则自动生成）" size="large"/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>用户名 *</label>
              <el-input v-model="newDatasource.username" placeholder="请输入数据库用户名" size="large"/>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>密码 *</label>
              <el-input v-model="newDatasource.password" placeholder="请输入数据库密码" size="large" show-password/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="30">
          <el-col :span="24">
            <div class="form-item">
              <label>描述</label>
              <el-input
                  v-model="newDatasource.description"
                  :rows="4"
                  type="textarea"
                  placeholder="请输入数据源描述（可选）"
                  size="large"
              />
            </div>
          </el-col>
        </el-row>

        <el-divider/>
        <div style="text-align: right;">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="createNewDatasource">
            创建并添加
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script lang="ts">
import {defineComponent, ref, onMounted, Ref, watch} from "vue"
import { Plus, UploadFilled } from '@element-plus/icons-vue'
import datasourceService from '@/services/datasource'
import { Datasource, AgentDatasource } from '@/services/datasource'
import { ApiResponse } from '@/services/common'
import { ElMessage, ElMessageBox } from 'element-plus'
import agentSchemaService from '@/services/schema'

export default defineComponent({
  name: 'AgentDataSourceConfig',
  props: {
    agentId: {
      type: Number,
      required: true
    }
  },
  setup(props) {
    // 当前Agent关联的数据源列表
    const datasource : Ref<Datasource[]> = ref([])
    const initStatus : Ref<boolean> = ref(false)
    const dialogVisible : Ref<boolean> = ref(false)
    const dialogActiveName : Ref<string> = ref('select')
    // 所有数据源列表
    const allDatasource : Ref<Datasource[]> = ref([])
    const newDatasource : Ref<Datasource> = ref({ port: 3306 } as Datasource)
    const selectedDatasourceId : Ref<number | null> = ref(null)

    watch(dialogVisible, (newValue) => {
      if (newValue) {
        loadAllDatasource()
        newDatasource.value = { port: 3306 } as Datasource
      }
    })

    // 初始化Agent数据源列表
    const loadAgentDatasource = async () => {
      selectedDatasourceId.value = null
      try {
        const response = await datasourceService.getAgentDatasource(props.agentId)
        const agentDatasource: AgentDatasource[] = response || []
        datasource.value = agentDatasource.map((item) => {
          const datasourceItem = { ...item.datasource };
          datasourceItem.status = item.isActive === 1 ? 'active' : 'inactive';
          return datasourceItem;
        })
      } catch (error) {
        ElMessage.error('加载当前智能体的数据源列表失败')
        console.error('Failed to load datasource:', error)
      }
    }

    const handleSelectDatasourceChange = (value: Datasource) => {
      if(value === null || value === undefined) {
        selectedDatasourceId.value = null
      } else {
        selectedDatasourceId.value = value.id
      }
    }

    const loadAllDatasource = async () => {
      try {
        const response = await datasourceService.getAllDatasource()
        allDatasource.value = response || []
      } catch (error) {
        ElMessage.error('加载所有数据源列表失败')
        console.error('Failed to load all datasource:', error)
      }
    }

    // 初始化Agent数据源
    const initAgentDatasource = async () => {
      initStatus.value = true
      try {

        // 获取智能体配置的启用数据源
        const usedDatasource : Datasource[] = datasource.value.filter((item) => item.status === 'active')
        if(!usedDatasource || usedDatasource.length === 0) {
          ElMessage.warning('当前智能体未启用任何数据源')
          return
        }

        // todo: 支持每一个数据源选择指定的数据表进行初始化（需要后端的配合）
        for (const source of usedDatasource) {
          ElMessage.primary(`正在初始化数据源: ${source.name}...`)
          const tables : ApiResponse<string[]> = await agentSchemaService.getDatasourceTables(props.agentId, source.id);
          if(tables === null || tables.data === null || !tables.success) {
            throw new Error('获取数据源表失败')
          }
          const tablesData: string[] = tables.data;
          if(tablesData.length === 0) {
            ElMessage.warning(`数据源: ${source.name} 没有数据表`)
            continue
          }

          const response: any = await agentSchemaService.initAgentSchema(props.agentId, source.id, tablesData);
          if(response.success === undefined || response.success == null || !response.success) {
            ElMessage.error(`初始化数据源: ${source.name} 失败`)
            throw new Error('初始化数据源失败')
          }
        }

        ElMessage.success('初始化当前智能体的数据源成功')
      } catch (error) {
        ElMessage.error('初始化当前智能体的数据源失败')
        console.error('Failed to init datasource:', error)
      } finally {
        initStatus.value = false
      }
    }

    // 更改数据源状态
    const changeDatasource = async (row : Datasource, active : boolean) => {
      const datasourceId = row.id;
      try {
        const response : ApiResponse = await datasourceService.toggleDatasourceForAgent(props.agentId, datasourceId, active);
        if(response.success) {
          ElMessage.success('操作成功！')
          row.status = active ? 'active' : 'inactive'
        } else {
          ElMessage.error('操作失败！')
          console.error('Failed to change datasource:', response)
        }
      } catch (error) {
        ElMessage.error('操作失败！')
        console.error('Failed to change datasource:', error)
      }
    }

    // 测试数据源连接
    const testConnection = async (row : Datasource) => {
      const datasourceId = row.id;
      try {
        const response : ApiResponse = await datasourceService.testConnection(datasourceId);
        if(response.success) {
          ElMessage.success('测试连接成功！')
          row.testStatus = 'success'
        } else {
          ElMessage.error('测试连接失败！')
          console.error('Failed to test connection:', response)
          row.testStatus = 'fail'
        }
      } catch (error) {
        ElMessage.error('测试连接失败！');
        console.error('Failed to test connection:', error)
        row.testStatus = 'fail'
      }
    }

    // 移除Agent数据源
    const removeAgentDatasource = async (row: Datasource) => {
      const datasourceId = row.id;

      try {
        await ElMessageBox.confirm('是否要删除当前数据源吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return
      }

      try {
        const response : ApiResponse = await datasourceService.removeDatasourceFromAgent(props.agentId, datasourceId);
        if(response.success) {
          ElMessage.success('移除成功！')
          datasource.value = datasource.value.filter((item) => item.id !== datasourceId)
        } else {
          ElMessage.error('移除失败！')
          console.error('Failed to remove datasource:', response)
        }
      } catch (error) {
        ElMessage.error('移除失败！')
        console.error('Failed to remove datasource:', error)
      }
    }

    const addDatasourceToAgent = async (datasourceId: number) => {
      try {
        await datasourceService.addDatasourceToAgent(props.agentId, datasourceId)
        await loadAgentDatasource()
        ElMessage.success('添加数据源成功')
        dialogVisible.value = false
      } catch (error) {
        ElMessage.error('添加数据源失败')
        console.error('Failed to add datasource:', error)
      }
    }

    const addSelectDatasource = async () => {
      const datasourceId = selectedDatasourceId.value;
      if(datasourceId === null || datasourceId === undefined) {
        ElMessage.warning('请选择一个数据源')
        return
      }
      await addDatasourceToAgent(datasourceId)
    }

    const validateDatasourceForm = (datasourceForm : Datasource) : string[] => {
      const errors : string[] = []

      if (!datasourceForm.name || datasourceForm.name.trim() === '') {
        errors.push('数据源名称不能为空')
      }

      if (!datasourceForm.type) {
        errors.push('请选择数据源类型')
      }

      if (!datasourceForm.host || datasourceForm.host.trim() === '') {
        errors.push('主机地址不能为空')
      }

      if (!datasourceForm.port || datasourceForm.port <= 0 || datasourceForm.port > 65535) {
        errors.push('请输入有效的端口号（1-65535）')
      }

      if (!datasourceForm.databaseName || datasourceForm.databaseName.trim() === '') {
        errors.push('数据库名不能为空')
      }

      if (!datasourceForm.username || datasourceForm.username.trim() === '') {
        errors.push('用户名不能为空')
      }

      if (!datasourceForm.password || datasourceForm.password.trim() === '') {
        errors.push('密码不能为空')
      }

      return errors
    }

    const createNewDatasource = async () => {
      const formErrors : string[] = validateDatasourceForm(newDatasource.value)
      if(formErrors.length > 0) {
        ElMessage.error(formErrors.join('\r\n'))
        return
      }
      try {
        const datasource : Datasource = await datasourceService.createDatasource(newDatasource.value)
        const id = datasource.id
        if(id === null || id === undefined) {
          throw new Error('创建数据源失败')
        }
        await addDatasourceToAgent(id)
      } catch (error) {
        ElMessage.error('创建数据源失败')
        console.error('Failed to create datasource:', error)
      }
      dialogVisible.value = false
    }

    onMounted(() => {
      loadAgentDatasource()
    })

    return {
      props,
      Plus,
      UploadFilled,
      datasource,
      initStatus,
      dialogVisible,
      dialogActiveName,
      allDatasource,
      newDatasource,
      initAgentDatasource,
      changeDatasource,
      testConnection,
      removeAgentDatasource,
      loadAllDatasource,
      addSelectDatasource,
      createNewDatasource,
      handleSelectDatasourceChange
    }
  }
})
</script>

<style scoped>

</style>
