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

/**
 * @description 文件上传服务，处理头像等静态资源的上传
 */

/**
 * @description 文件上传响应接口
 */
interface UploadResponse {
  /** 是否成功 */
  success: boolean;
  /** 提示消息 */
  message?: string;
  /** 上传后的文件 URL */
  url?: string;
}

/**
 * @description 文件上传 API 封装对象
 */
export const fileUploadApi = {
  /**
   * @description 上传用户头像
   * @param {File} file - 头像文件对象
   * @returns {Promise<UploadResponse>} 上传结果
   */
  uploadAvatar(file: File): Promise<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const url = '/api/upload/avatar';
    return fetch(url, {
      method: 'POST',
      body: formData,
    }).then(async response => {
      if (!response.ok) {
        const text = await response.text().catch(() => '');
        throw new Error(`Upload failed: ${response.status} ${text}`);
      }
      const ct = response.headers.get('content-type') || '';
      if (ct.includes('application/json')) {
        return await response.json();
      }
      const text = await response.text();
      return { success: true, message: 'ok', url: text };
    });
  },
};
