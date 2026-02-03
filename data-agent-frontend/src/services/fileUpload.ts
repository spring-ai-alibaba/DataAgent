/*
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
 */
import axios from 'axios';
import {ApiResponse} from "@/services/common.ts";

/**
 * 业务API服务
 * 封装所有业务相关的API调用
 */

interface FileStorage {
  id: number;
  filePath?: string;
  url: string;
  filename?: string;
}

export type FileUploadResult = ApiResponse<FileStorage>;

// 文件上传API
export const fileUploadApi = {
  // 上传头像
  async uploadAvatar(file: File): Promise<FileStorage | null> {
    const formData = new FormData();
    formData.append('file', file);
    const url = '/api/upload/avatar';
    try {
      const response = await axios.post<FileUploadResult>(url, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      if (response.data.success) {
        return response.data.data ?? null;
      }
      throw new Error(response.data.message);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },
};
