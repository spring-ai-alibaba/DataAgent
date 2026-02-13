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
  <div class="base-layout">
    <!-- 现代化头部导航 -->
    <header class="page-header">
      <div class="header-content">
        <div class="brand-section">
          <div class="brand-logo">
            <i class="bi bi-robot"></i>
            <span class="brand-text">Spring AI Alibaba Data Agent</span>
          </div>
          <nav class="header-nav">
            <div class="nav-item" :class="{ active: isAgentPage() }" @click="goToAgentList">
              <i class="bi bi-grid-3x3-gap"></i>
              <span>智能体列表</span>
            </div>
            <div class="nav-item" :class="{ active: isModelConfigPage() }" @click="goToModelConfig">
              <i class="bi bi-gear"></i>
              <span>模型配置</span>
            </div>
          </nav>
        </div>
        <div class="user-section">
          <el-dropdown @command="handleUserCommand">
            <span class="user-info">
              <el-avatar :size="28" :src="user?.avatar" style="background-color: #5f70e1">
                {{ user?.username?.charAt(0)?.toUpperCase() || 'U' }}
              </el-avatar>
              <span class="username">{{ user?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span style="color: #909399; font-size: 12px">{{ user?.email || '' }}</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="user?.userType === 1" command="createUser">
                  创建用户
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 页面内容区域 -->
    <main class="page-content">
      <slot></slot>
    </main>

    <!-- 创建用户弹窗 -->
    <el-dialog
      v-model="createUserVisible"
      title="创建用户"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createUserFormRef"
        :model="createUserForm"
        :rules="createUserRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createUserForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="createUserForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createUserForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createUserForm.phone" placeholder="请输入手机号（选填）" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="createUserForm.realName" placeholder="请输入真实姓名（选填）" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="createUserForm.roleCode" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="数据分析师" value="ANALYST" />
            <el-option label="观察者（只读）" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createUserVisible = false">取消</el-button>
        <el-button type="primary" :loading="createUserLoading" @click="handleCreateUser">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
  import { ref, reactive, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { ArrowDown } from '@element-plus/icons-vue';
  import { ElMessage } from 'element-plus';
  import authService from '@/services/auth';

  export default {
    name: 'BaseLayout',
    components: { ArrowDown },
    setup() {
      const router = useRouter();
      const user = ref(authService.getUser());

      // 导航方法
      const goToAgentList = () => {
        router.push('/agents');
      };

      const goToModelConfig = () => {
        router.push('/model-config');
      };

      const isAgentPage = () => {
        return (
          router.currentRoute.value.name === 'AgentList' ||
          router.currentRoute.value.name === 'AgentDetail' ||
          router.currentRoute.value.name === 'AgentCreate' ||
          router.currentRoute.value.name === 'AgentRun'
        );
      };

      const isModelConfigPage = () => {
        return router.currentRoute.value.name === 'ModelConfig';
      };

      const handleUserCommand = async command => {
        if (command === 'logout') {
          await authService.logout();
          router.push('/login');
        } else if (command === 'createUser') {
          createUserVisible.value = true;
        }
      };

      // 创建用户相关
      const createUserVisible = ref(false);
      const createUserLoading = ref(false);
      const createUserFormRef = ref(null);
      const createUserForm = reactive({
        username: '',
        password: '',
        email: '',
        phone: '',
        realName: '',
        roleCode: 'ANALYST',
      });
      const createUserRules = {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, message: '密码至少6位', trigger: 'blur' },
        ],
        email: [
          { required: true, message: '请输入邮箱', trigger: 'blur' },
          { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
        ],
        roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
      };
      const handleCreateUser = async () => {
        if (!createUserFormRef.value) return;
        try {
          await createUserFormRef.value.validate();
        } catch {
          return;
        }
        createUserLoading.value = true;
        try {
          await authService.createUser({
            username: createUserForm.username,
            password: createUserForm.password,
            email: createUserForm.email,
            phone: createUserForm.phone || undefined,
            realName: createUserForm.realName || undefined,
            roleCode: createUserForm.roleCode,
            userType: createUserForm.roleCode === 'ADMIN' ? 1 : 0,
          });
          ElMessage.success('用户创建成功');
          createUserVisible.value = false;
          createUserForm.username = '';
          createUserForm.password = '';
          createUserForm.email = '';
          createUserForm.phone = '';
          createUserForm.realName = '';
          createUserForm.roleCode = 'ANALYST';
        } catch (error) {
          ElMessage.error(error.message || '创建用户失败');
        } finally {
          createUserLoading.value = false;
        }
      };

      onMounted(async () => {
        try {
          user.value = await authService.getCurrentUser();
        } catch (e) {
          // token 可能无效，忽略
        }
      });

      return {
        user,
        goToAgentList,
        goToModelConfig,
        isAgentPage,
        isModelConfigPage,
        handleUserCommand,
        createUserVisible,
        createUserLoading,
        createUserFormRef,
        createUserForm,
        createUserRules,
        handleCreateUser,
      };
    },
  };
</script>

<style scoped>
  .base-layout {
    min-height: 100vh;
    background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  }

  .page-header {
    background: white;
    border-bottom: 1px solid #e2e8f0;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    position: sticky;
    top: 0;
    z-index: 100;
  }

  .header-content {
    width: 100%;
    padding: 0 1.5rem;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 4rem;
  }

  .brand-section {
    display: flex;
    align-items: center;
    gap: 2rem;
  }

  .brand-logo {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    font-size: 1.25rem;
    font-weight: 600;
    color: #1e293b;
  }

  .brand-logo i {
    font-size: 1.5rem;
    color: #3b82f6;
  }

  .header-nav {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .nav-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s ease;
    color: #64748b;
    font-weight: 500;
  }

  .nav-item:hover {
    background: #f1f5f9;
    color: #334155;
  }

  .nav-item.active {
    background: #e0f2fe;
    color: #0369a1;
  }

  .nav-item i {
    font-size: 1rem;
  }

  .user-section {
    display: flex;
    align-items: center;
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    cursor: pointer;
    padding: 0.25rem 0.5rem;
    border-radius: 8px;
    transition: background 0.2s ease;
  }

  .user-info:hover {
    background: #f1f5f9;
  }

  .username {
    font-size: 0.875rem;
    font-weight: 500;
    color: #334155;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .page-content {
    flex: 1;
    padding: 0;
  }
</style>
