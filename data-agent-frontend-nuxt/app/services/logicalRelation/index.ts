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
 * @description 逻辑外键管理服务，处理数据源表之间的关联关系定义及字段同步
 */

import axios from 'axios';
import type { ApiResponse } from './common';

/**
 * @description 逻辑外键关系实体接口
 */
export interface LogicalRelation {
  /** 关系 ID */
  id?: number;
  /** 关联的数据源 ID */
  datasourceId?: number;
  /** 源表名 */
  sourceTableName: string;
  /** 源列名 */
  sourceColumnName: string;
  /** 目标表名 */
  targetTableName: string;
  /** 目标列名 */
  targetColumnName: string;
  /** 关系类型 (1:1, 1:N, N:1) */
  relationType?: string;
  /** 描述 */
  description?: string;
  /** 是否已删除 (0: 否, 1: 是) */
  isDeleted?: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}

const API_BASE_URL = '/api/datasource';

/**
 * @description 逻辑外键业务逻辑处理类
 */
class LogicalRelationService {
  /**
   * @description 获取指定数据源的所有逻辑外键列表
   * @param {number} datasourceId - 数据源 ID
   * @returns {Promise<LogicalRelation[]>} 逻辑外键列表
   */
  async getLogicalRelations(datasourceId: number): Promise<LogicalRelation[]> {
    try {
      const response = await axios.get<ApiResponse<LogicalRelation[]>>(
        `${API_BASE_URL}/${datasourceId}/logical-relations`,
      );
      return response.data.data || [];
    } catch (error) {
      console.error('Failed to get logical relations:', error);
      return [];
    }
  }

  /**
   * @description 为数据源添加新的逻辑外键
   * @param {number} datasourceId - 数据源 ID
   * @param {Omit<LogicalRelation, 'id' | 'datasourceId' | 'isDeleted' | 'createdTime' | 'updatedTime'>} logicalRelation - 关系信息
   * @returns {Promise<LogicalRelation | null>} 创建成功的关系详情
   */
  async addLogicalRelation(
    datasourceId: number,
    logicalRelation: Omit<
      LogicalRelation,
      'id' | 'datasourceId' | 'isDeleted' | 'createdTime' | 'updatedTime'
    >,
  ): Promise<LogicalRelation | null> {
    try {
      const response = await axios.post<ApiResponse<LogicalRelation>>(
        `${API_BASE_URL}/${datasourceId}/logical-relations`,
        logicalRelation,
      );
      return response.data.data || null;
    } catch (error) {
      console.error('Failed to add logical relation:', error);
      throw error;
    }
  }

  /**
   * @description 更新现有的逻辑外键信息
   * @param {number} datasourceId - 数据源 ID
   * @param {number} relationId - 关系 ID
   * @param {Omit<LogicalRelation, 'id' | 'datasourceId' | 'isDeleted' | 'createdTime' | 'updatedTime'>} logicalRelation - 更新字段
   * @returns {Promise<ApiResponse<LogicalRelation>>} 操作结果
   */
  async updateLogicalRelation(
    datasourceId: number,
    relationId: number,
    logicalRelation: Omit<
      LogicalRelation,
      'id' | 'datasourceId' | 'isDeleted' | 'createdTime' | 'updatedTime'
    >,
  ): Promise<ApiResponse<LogicalRelation>> {
    const response = await axios.put<ApiResponse<LogicalRelation>>(
      `${API_BASE_URL}/${datasourceId}/logical-relations/${relationId}`,
      logicalRelation,
    );
    return response.data;
  }

  /**
   * @description 删除指定的逻辑外键
   * @param {number} datasourceId - 数据源 ID
   * @param {number} relationId - 关系 ID
   * @returns {Promise<ApiResponse<void>>} 操作结果
   */
  async deleteLogicalRelation(
    datasourceId: number,
    relationId: number,
  ): Promise<ApiResponse<void>> {
    const response = await axios.delete<ApiResponse<void>>(
      `${API_BASE_URL}/${datasourceId}/logical-relations/${relationId}`,
    );
    return response.data;
  }

  /**
   * @description 批量保存逻辑外键 (全量替换)
   * @param {number} datasourceId - 数据源 ID
   * @param {LogicalRelation[]} logicalRelations - 新的关系列表
   * @returns {Promise<ApiResponse<LogicalRelation[]>>} 保存后的关系列表
   */
  async saveLogicalRelations(
    datasourceId: number,
    logicalRelations: LogicalRelation[],
  ): Promise<ApiResponse<LogicalRelation[]>> {
    const response = await axios.put<ApiResponse<LogicalRelation[]>>(
      `${API_BASE_URL}/${datasourceId}/logical-relations`,
      logicalRelations,
    );
    return response.data;
  }

  /**
   * @description 获取指定数据源表的列名列表
   * @param {number} datasourceId - 数据源 ID
   * @param {string} tableName - 表名
   * @returns {Promise<string[]>} 列名列表
   */
  async getTableColumns(datasourceId: number, tableName: string): Promise<string[]> {
    try {
      const response = await axios.get<ApiResponse<string[]>>(
        `${API_BASE_URL}/${datasourceId}/tables/${tableName}/columns`,
      );
      if (response.data.success) {
        return response.data.data || [];
      }
      throw new Error(response.data.message);
    } catch (error) {
      console.error('Failed to get table columns:', error);
      return [];
    }
  }
}

export default new LogicalRelationService();
