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
  <div class="login-page">
    <div class="login-card">
      <div class="brand-header">
        <i class="bi bi-robot"></i>
        <span>Spring AI Alibaba Data Agent</span>
      </div>

      <p class="login-subtitle">登录到您的账户</p>

      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loginLoading" class="submit-btn" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script>
import { ref, reactive } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
import authService from '@/services/auth';

export default {
  name: 'Login',
  setup() {
    const router = useRouter();
    const route = useRoute();

    const loginLoading = ref(false);
    const loginFormRef = ref(null);

    const loginForm = reactive({
      username: '',
      password: '',
    });

    const loginRules = {
      username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
      password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
    };

    const handleLogin = async () => {
      if (!loginFormRef.value) return;
      try {
        await loginFormRef.value.validate();
      } catch {
        return;
      }

      loginLoading.value = true;
      try {
        await authService.login({
          username: loginForm.username,
          password: loginForm.password,
        });
        ElMessage.success('登录成功');
        const redirect = route.query.redirect || '/agents';
        router.push(redirect);
      } catch (error) {
        const msg = error.response?.data?.message || error.message || '登录失败';
        ElMessage.error(msg);
      } finally {
        loginLoading.value = false;
      }
    };

    return {
      User,
      Lock,
      loginLoading,
      loginFormRef,
      loginForm,
      loginRules,
      handleLogin,
    };
  },
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 50%, #f1f5f9 100%);
}

.login-card {
  width: 420px;
  padding: 2.5rem;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.brand-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.brand-header i {
  font-size: 1.5rem;
  color: #3b82f6;
}

.login-subtitle {
  text-align: center;
  color: #64748b;
  font-size: 0.875rem;
  margin-bottom: 2rem;
}

.submit-btn {
  width: 100%;
  margin-top: 0.5rem;
}
</style>
