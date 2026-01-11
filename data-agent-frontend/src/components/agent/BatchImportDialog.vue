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
  <el-dialog v-model="dialogVisible" :title="title" width="900">
    <el-alert title="导入说明" type="info" :closable="false" style="margin-bottom: 20px">
      <p>1. 支持JSON和Excel两种导入方式</p>
      <p>2. 如果记录已存在（相同表名+字段名），将自动覆盖更新</p>
      <p>3. 枚举字段信息可以直接写在"业务描述"中，例如：枚举值：1=资产工单, 2=账号工单</p>
      <p>4. 必填字段：表名、字段名、业务名称、数据类型</p>
    </el-alert>

    <el-radio-group v-model="importMode" style="margin-bottom: 20px">
      <el-radio-button value="json">JSON导入</el-radio-button>
      <el-radio-button value="excel">Excel导入</el-radio-button>
    </el-radio-group>

    <el-tabs v-model="importTab">
      <el-tab-pane label="JSON输入" name="input" v-if="importMode === 'json'">
        <el-input
          v-model="importJsonText"
          type="textarea"
          :rows="15"
          placeholder="请输入JSON格式的数据..."
          style="font-family: 'Courier New', monospace"
        />
        <div style="margin-top: 10px; text-align: right">
          <el-button @click="loadTemplate" size="small">加载模板</el-button>
          <el-button @click="handleValidateJson" size="small" type="primary">验证JSON</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Excel上传" name="input" v-if="importMode === 'excel'">
        <div style="text-align: center; padding: 40px 0">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              将Excel文件拖到此处，或
              <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">只能上传 xlsx/xls 文件，且不超过 10MB</div>
            </template>
          </el-upload>
          <div style="margin-top: 20px">
            <el-button @click="downloadExcelTemplate" type="primary" plain>下载Excel模板</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="导入结果" name="result" :disabled="!importResult">
        <el-result
          v-if="importResult"
          :icon="importResult.failCount === 0 ? 'success' : 'warning'"
          :title="importResult.failCount === 0 ? '导入成功' : '部分导入失败'"
        >
          <template #sub-title>
            <p>
              总数：{{ importResult.total }} | 成功：{{ importResult.successCount }} | 失败：{{
                importResult.failCount
              }}
            </p>
          </template>
          <template #extra>
            <div
              v-if="importResult.errors.length > 0"
              style="text-align: left; max-height: 300px; overflow-y: auto"
            >
              <el-alert
                v-for="(error, index) in importResult.errors"
                :key="index"
                :title="error"
                type="error"
                :closable="false"
                style="margin-bottom: 10px"
              />
            </div>
          </template>
        </el-result>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          @click="handleJsonImport"
          :loading="importing"
          v-if="importMode === 'json'"
        >
          开始导入
        </el-button>
        <el-button
          type="primary"
          @click="handleExcelImport"
          :loading="importing"
          v-if="importMode === 'excel'"
        >
          开始导入
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts">
  import { defineComponent, ref, watch, computed, PropType } from 'vue';
  import { UploadFilled } from '@element-plus/icons-vue';
  import { ElMessage } from 'element-plus';
  import type { UploadFile } from 'element-plus';
  import type { BatchImportResult } from '@/services/semanticModel';

  export default defineComponent({
    name: 'BatchImportDialog',
    components: {
      UploadFilled,
    },
    props: {
      modelValue: {
        type: Boolean,
        required: true,
      },
      title: {
        type: String,
        default: '批量导入',
      },
      jsonTemplate: {
        type: Array as PropType<unknown[]>,
        default: () => [],
      },
      validateJson: {
        type: Function as PropType<(jsonText: string) => boolean>,
        required: true,
      },
      validateExcelFile: {
        type: Function as PropType<(file: File | null) => boolean>,
        required: true,
      },
      onJsonImport: {
        type: Function as PropType<(items: unknown[]) => Promise<BatchImportResult>>,
        required: true,
      },
      onExcelImport: {
        type: Function as PropType<(file: File) => Promise<BatchImportResult>>,
        required: true,
      },
      onDownloadExcelTemplate: {
        type: Function as PropType<() => Promise<void>>,
        required: true,
      },
    },
    emits: ['update:modelValue', 'imported'],
    setup(props, { emit }) {
      const importJsonText = ref('');
      const importTab = ref('input');
      const importResult = ref<BatchImportResult | null>(null);
      const importing = ref(false);
      const importMode = ref('json');
      const uploadedFile = ref<File | null>(null);

      const dialogVisible = computed({
        get: () => props.modelValue,
        set: (value: boolean) => emit('update:modelValue', value),
      });

      const resetState = () => {
        importTab.value = 'input';
        importResult.value = null;
        importJsonText.value = '';
        importMode.value = 'json';
        uploadedFile.value = null;
      };

      watch(
        () => props.modelValue,
        value => {
          if (value) {
            resetState();
          }
        },
      );

      const loadTemplate = () => {
        importJsonText.value = JSON.stringify(props.jsonTemplate, null, 2);
        ElMessage.success('模板已加载');
      };

      const handleValidateJson = () => props.validateJson(importJsonText.value);

      const handleJsonImport = async () => {
        if (!props.validateJson(importJsonText.value)) {
          return;
        }

        let items: unknown[];
        try {
          items = JSON.parse(importJsonText.value);
        } catch (error) {
          ElMessage.error('JSON格式错误：' + (error as Error).message);
          return;
        }

        try {
          importing.value = true;
          const result = await props.onJsonImport(items);
          importResult.value = result;
          importTab.value = 'result';

          if (result.failCount === 0) {
            ElMessage.success(`批量导入成功！共导入${result.successCount}条记录`);
          } else {
            ElMessage.warning(
              `批量导入完成！成功${result.successCount}条，失败${result.failCount}条`,
            );
          }
          emit('imported', result);
        } catch (error) {
          ElMessage.error('批量导入失败：' + (error as Error).message);
          console.error('Failed to batch import:', error);
        } finally {
          importing.value = false;
        }
      };

      const handleFileChange = (file: UploadFile) => {
        uploadedFile.value = file.raw ?? null;
      };

      const handleExceed = () => {
        ElMessage.warning('只能上传一个文件');
      };

      const downloadExcelTemplate = async () => {
        try {
          await props.onDownloadExcelTemplate();
          ElMessage.success('模板下载成功');
        } catch (error) {
          ElMessage.error('模板下载失败：' + (error as Error).message);
          console.error('Failed to download template:', error);
        }
      };

      const handleExcelImport = async () => {
        if (!props.validateExcelFile(uploadedFile.value) || !uploadedFile.value) {
          return;
        }

        try {
          importing.value = true;
          const result = await props.onExcelImport(uploadedFile.value);
          importResult.value = result;
          importTab.value = 'result';

          if (result.failCount === 0) {
            ElMessage.success(`Excel导入成功！共导入${result.successCount}条记录`);
          } else {
            ElMessage.warning(
              `Excel导入完成！成功${result.successCount}条，失败${result.failCount}条`,
            );
          }
          emit('imported', result);
        } catch (error) {
          ElMessage.error('Excel导入失败：' + (error as Error).message);
          console.error('Failed to import excel:', error);
        } finally {
          importing.value = false;
        }
      };

      return {
        dialogVisible,
        importJsonText,
        importTab,
        importResult,
        importing,
        importMode,
        loadTemplate,
        handleValidateJson,
        handleJsonImport,
        handleFileChange,
        handleExceed,
        downloadExcelTemplate,
        handleExcelImport,
      };
    },
  });
</script>
