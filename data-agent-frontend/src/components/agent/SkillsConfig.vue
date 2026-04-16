<!--
 * Copyright 2024-2026 the original author or authors.
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
  <div class="agent-skills-config" v-loading="loading">
    <div class="page-header">
      <div>
        <h2>技能配置</h2>
        <p>本地 skills 存放在专门目录中。你可以为当前智能体开启多个 skill，也可以在这里管理自定义 skill。</p>
      </div>
      <div class="header-actions">
        <el-button @click="loadSkillConfig">刷新</el-button>
        <el-button type="primary" :loading="savingBindings" @click="saveAgentSkills">
          保存启用项
        </el-button>
      </div>
    </div>

    <section class="config-section">
      <div class="section-title">
        <h3>当前智能体已启用的技能</h3>
        <span>勾选后保存，运行智能体时会注入选中的 AgentScope skills。</span>
      </div>
      <el-empty v-if="skills.length === 0" description="本地尚未发现任何 skill" />
      <el-checkbox-group v-else v-model="selectedSkillIds" class="skill-grid">
        <el-card v-for="skill in skills" :key="skill.id" shadow="hover" class="skill-card">
          <div class="skill-card-header">
            <div>
              <div class="skill-title-row">
                <h4>{{ skill.title }}</h4>
                <el-tag v-if="skill.builtin" size="small" type="success">内置</el-tag>
                <el-tag v-else size="small" type="info">自定义</el-tag>
              </div>
              <div class="skill-meta">
                <span>{{ skill.id }}</span>
                <span>资源 {{ skill.resourceCount }}</span>
                <span v-if="skill.updateTime">更新于 {{ formatTime(skill.updateTime) }}</span>
              </div>
            </div>
            <el-checkbox :label="skill.id">启用</el-checkbox>
          </div>
          <p class="skill-description">{{ skill.description }}</p>
        </el-card>
      </el-checkbox-group>
    </section>

    <section class="config-section">
      <div class="section-title">
        <div>
          <h3>本地 Skills 管理</h3>
          <span>支持新增、编辑、查看和删除自定义 skill。内置示例 skill 为只读。</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">新增自定义 Skill</el-button>
      </div>

      <el-table :data="skills" stripe border class="skills-table">
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <div class="table-title">{{ row.title }}</div>
            <div class="table-subtitle">{{ row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="280" prop="description" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.builtin ? 'success' : 'info'" size="small">
              {{ row.builtin ? '内置' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源数" width="90" prop="resourceCount" />
        <el-table-column label="更新时间" min-width="160">
          <template #default="{ row }">
            {{ row.updateTime ? formatTime(row.updateTime) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openViewDialog(row.id)">查看</el-button>
              <el-button
                link
                type="primary"
                :disabled="row.builtin"
                @click="openEditDialog(row.id)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="row.builtin"
                @click="handleDeleteSkill(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增本地 Skill' : dialogReadonly ? '查看 Skill' : '编辑 Skill'"
      width="760px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item v-if="dialogMode === 'create'" label="Skill ID（可选）">
          <el-input
            v-model="skillForm.id"
            :disabled="dialogReadonly"
            placeholder="留空时会根据标题自动生成，例如 sales-analyst"
          />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="skillForm.title" :disabled="dialogReadonly" placeholder="例如：销售分析助手" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="skillForm.description"
            :disabled="dialogReadonly"
            placeholder="描述模型应该在什么场景下启用这个 skill"
          />
        </el-form-item>
        <el-form-item label="Skill 内容">
          <el-input
            v-model="skillForm.content"
            :disabled="dialogReadonly"
            type="textarea"
            :rows="14"
            placeholder="这里填写 skill 的正文内容。系统会自动写入 SKILL.md 的 front matter 和标题。"
          />
        </el-form-item>
        <el-form-item label="资源文件">
          <el-tag
            v-for="resource in skillForm.resources"
            :key="resource"
            size="small"
            effect="plain"
            class="resource-tag"
          >
            {{ resource }}
          </el-tag>
          <span v-if="skillForm.resources.length === 0" class="empty-resource">暂无资源文件</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button
            v-if="!dialogReadonly"
            type="primary"
            :loading="savingSkill"
            @click="submitSkill"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
  import { defineComponent, onMounted, reactive, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import skillService, {
    type AgentSkillConfig,
    type LocalSkillDetail,
    type LocalSkillSummary,
  } from '@/services/skill';

  type DialogMode = 'create' | 'edit' | 'view';

  export default defineComponent({
    name: 'AgentSkillsConfig',
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const loading = ref(false);
      const savingBindings = ref(false);
      const savingSkill = ref(false);
      const storagePath = ref('');
      const skills = ref<LocalSkillSummary[]>([]);
      const selectedSkillIds = ref<string[]>([]);

      const dialogVisible = ref(false);
      const dialogMode = ref<DialogMode>('create');
      const currentSkillId = ref<string>('');
      const skillForm = reactive({
        id: '',
        title: '',
        description: '',
        content: '',
        resources: [] as string[],
      });

      const dialogReadonly = ref(false);

      const applyConfig = (config: AgentSkillConfig) => {
        storagePath.value = config.storagePath;
        skills.value = config.skills;
        selectedSkillIds.value = [...config.selectedSkillIds];
      };

      const resetSkillForm = () => {
        skillForm.id = '';
        skillForm.title = '';
        skillForm.description = '';
        skillForm.content = '';
        skillForm.resources = [];
      };

      const loadSkillConfig = async () => {
        try {
          loading.value = true;
          const config = await skillService.getAgentSkillConfig(props.agentId);
          applyConfig(config);
        } catch (error) {
          console.error('加载技能配置失败', error);
          ElMessage.error('加载技能配置失败');
        } finally {
          loading.value = false;
        }
      };

      const saveAgentSkills = async () => {
        try {
          savingBindings.value = true;
          const config = await skillService.updateAgentSkills(props.agentId, selectedSkillIds.value);
          applyConfig(config);
          ElMessage.success('技能启用项已保存');
        } catch (error) {
          console.error('保存技能启用项失败', error);
          ElMessage.error('保存技能启用项失败');
        } finally {
          savingBindings.value = false;
        }
      };

      const populateDialog = (detail: LocalSkillDetail) => {
        currentSkillId.value = detail.id;
        skillForm.id = detail.id;
        skillForm.title = detail.title;
        skillForm.description = detail.description;
        skillForm.content = detail.content;
        skillForm.resources = detail.resources || [];
      };

      const loadSkillDetail = async (skillId: string, mode: DialogMode) => {
        try {
          loading.value = true;
          const detail = await skillService.get(skillId);
          populateDialog(detail);
          dialogMode.value = mode;
          dialogReadonly.value = mode === 'view' || detail.builtin;
          dialogVisible.value = true;
        } catch (error) {
          console.error('加载 skill 详情失败', error);
          ElMessage.error('加载 skill 详情失败');
        } finally {
          loading.value = false;
        }
      };

      const openCreateDialog = () => {
        resetSkillForm();
        dialogMode.value = 'create';
        dialogReadonly.value = false;
        dialogVisible.value = true;
      };

      const openEditDialog = async (skillId: string) => {
        await loadSkillDetail(skillId, 'edit');
      };

      const openViewDialog = async (skillId: string) => {
        await loadSkillDetail(skillId, 'view');
      };

      const submitSkill = async () => {
        if (!skillForm.title.trim() || !skillForm.description.trim() || !skillForm.content.trim()) {
          ElMessage.warning('请填写完整的标题、描述和 skill 内容');
          return;
        }
        try {
          savingSkill.value = true;
          if (dialogMode.value === 'create') {
            await skillService.create({
              id: skillForm.id.trim() || undefined,
              title: skillForm.title.trim(),
              description: skillForm.description.trim(),
              content: skillForm.content.trim(),
            });
            ElMessage.success('自定义 skill 创建成功');
          } else {
            await skillService.update(currentSkillId.value, {
              title: skillForm.title.trim(),
              description: skillForm.description.trim(),
              content: skillForm.content.trim(),
            });
            ElMessage.success('自定义 skill 更新成功');
          }
          dialogVisible.value = false;
          await loadSkillConfig();
        } catch (error) {
          console.error('保存自定义 skill 失败', error);
          ElMessage.error('保存自定义 skill 失败');
        } finally {
          savingSkill.value = false;
        }
      };

      const handleDeleteSkill = async (skill: LocalSkillSummary) => {
        try {
          await ElMessageBox.confirm(
            `确定要删除自定义 skill "${skill.title}" 吗？删除后会从所有智能体配置中移除。`,
            '删除 Skill',
            {
              confirmButtonText: '删除',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );
          await skillService.delete(skill.id);
          ElMessage.success('自定义 skill 已删除');
          await loadSkillConfig();
        } catch (error) {
          if (error !== 'cancel' && error !== 'close') {
            console.error('删除自定义 skill 失败', error);
            ElMessage.error('删除自定义 skill 失败');
          }
        }
      };

      const formatTime = (value: string): string => {
        if (!value) return '-';
        return new Date(value).toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        });
      };

      onMounted(async () => {
        await loadSkillConfig();
      });

      return {
        loading,
        savingBindings,
        savingSkill,
        storagePath,
        skills,
        selectedSkillIds,
        dialogVisible,
        dialogMode,
        dialogReadonly,
        skillForm,
        loadSkillConfig,
        saveAgentSkills,
        openCreateDialog,
        openEditDialog,
        openViewDialog,
        submitSkill,
        handleDeleteSkill,
        formatTime,
      };
    },
  });
</script>

<style scoped>
  .agent-skills-config {
    padding: 20px;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    align-items: flex-start;
    margin-bottom: 20px;
  }

  .page-header h2 {
    margin: 0 0 8px;
  }

  .page-header p {
    margin: 0;
    color: #606266;
    line-height: 1.6;
  }

  .header-actions {
    display: flex;
    gap: 12px;
  }

  .storage-alert {
    margin-bottom: 24px;
  }

  .config-section + .config-section {
    margin-top: 28px;
  }

  .section-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;
  }

  .section-title h3 {
    margin: 0 0 4px;
  }

  .section-title span {
    color: #606266;
    font-size: 13px;
  }

  .skill-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 16px;
  }

  .skill-card {
    border-radius: 14px;
  }

  .skill-card :deep(.el-card__body) {
    padding: 18px;
  }

  .skill-card-header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: flex-start;
  }

  .skill-title-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;
  }

  .skill-title-row h4 {
    margin: 0;
    font-size: 16px;
  }

  .skill-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    color: #909399;
    font-size: 12px;
  }

  .skill-description {
    margin: 14px 0 0;
    color: #606266;
    line-height: 1.7;
  }

  .skills-table {
    width: 100%;
  }

  .table-title {
    font-weight: 600;
    color: #303133;
  }

  .table-subtitle {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
  }

  .table-actions {
    display: flex;
    gap: 8px;
  }

  .resource-tag {
    margin-right: 8px;
    margin-bottom: 8px;
  }

  .empty-resource {
    color: #909399;
  }

  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }

  @media (max-width: 900px) {
    .page-header,
    .section-title {
      flex-direction: column;
      align-items: stretch;
    }

    .header-actions {
      justify-content: flex-end;
    }
  }
</style>