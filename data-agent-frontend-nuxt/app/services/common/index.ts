/*
 * Copyright 2024-2025 the original author or authors.
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
 * @description 通用 API 响应结构定义
 */

/**
 * @description 基础 API 响应接口
 * @template T 数据类型
 */
export interface ApiResponse<T = unknown> {
  /** 是否成功 */
  success: boolean;
  /** 提示消息 */
  message: string;
  /** 返回数据 */
  data?: T;
}

/**
 * @description 分页 API 响应接口
 * @template T 数据类型
 */
export interface PageResponse<T = unknown> {
  /** 是否成功 */
  success: boolean;
  /** 提示消息 */
  message: string;
  /** 数据列表 */
  data: T;
  /** 总条数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 每页条数 */
  pageSize: number;
  /** 总页数 */
  totalPages: number;
}
