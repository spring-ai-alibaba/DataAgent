/*
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
 */

import axios from 'axios';
import { ApiResponse } from './common';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  realName?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: UserInfo;
}

export interface UserInfo {
  id: number;
  username: string;
  email: string;
  realName: string;
  avatar: string;
  userType: number;
  roles: string[];
}

const TOKEN_KEY = 'access_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
const USER_KEY = 'user_info';

class AuthService {
  async login(data: LoginRequest): Promise<TokenResponse> {
    const response = await axios.post<ApiResponse<TokenResponse>>('/api/auth/login', data);
    if (response.data.success && response.data.data) {
      this.setTokens(response.data.data);
      return response.data.data;
    }
    throw new Error(response.data.message || '登录失败');
  }

  async register(data: RegisterRequest): Promise<TokenResponse> {
    const response = await axios.post<ApiResponse<TokenResponse>>('/api/auth/register', data);
    if (response.data.success && response.data.data) {
      this.setTokens(response.data.data);
      return response.data.data;
    }
    throw new Error(response.data.message || '注册失败');
  }

  async refreshToken(): Promise<TokenResponse> {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) throw new Error('No refresh token');
    const response = await axios.post<ApiResponse<TokenResponse>>('/api/auth/refresh', { refreshToken });
    if (response.data.success && response.data.data) {
      this.setTokens(response.data.data);
      return response.data.data;
    }
    throw new Error(response.data.message || '刷新失败');
  }

  async logout(): Promise<void> {
    try {
      await axios.post('/api/auth/logout');
    } finally {
      this.clearTokens();
    }
  }

  async getCurrentUser(): Promise<UserInfo> {
    const response = await axios.get<ApiResponse<UserInfo>>('/api/auth/me');
    if (response.data.success && response.data.data) {
      localStorage.setItem(USER_KEY, JSON.stringify(response.data.data));
      return response.data.data;
    }
    throw new Error(response.data.message || '获取用户信息失败');
  }

  getAccessToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getUser(): UserInfo | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }

  setTokens(data: TokenResponse): void {
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
    if (data.user) {
      localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    }
  }

  clearTokens(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
}

export default new AuthService();
