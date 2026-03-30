/**
 * @description 语义模型管理服务，处理库表字段的业务映射、批量导入导出及状态管理
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/**
 * @description 语义模型实体接口
 */
export interface SemanticModel {
  /** 模型 ID */
  id?: number;
  /** 关联的智能体 ID */
  agentId: number;
  /** 关联的数据源 ID */
  datasourceId?: number;
  /** 数据库表名 */
  tableName: string;
  /** 数据库列名 */
  columnName: string;
  /** 业务名称 */
  businessName: string;
  /** 同义词 */
  synonyms: string;
  /** 业务描述 */
  businessDescription: string;
  /** 数据库列注释 */
  columnComment: string;
  /** 数据类型 */
  dataType: string;
  /** 状态 (0: 禁用, 1: 启用) */
  status: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * @description 创建语义模型的 DTO
 */
export interface SemanticModelAddDto {
  /** 智能体 ID */
  agentId: number;
  /** 表名 */
  tableName: string;
  /** 列名 */
  columnName: string;
  /** 业务名称 */
  businessName: string;
  /** 同义词 */
  synonyms: string;
  /** 业务描述 */
  businessDescription: string;
  /** 列注释 */
  columnComment: string;
  /** 数据类型 */
  dataType: string;
}

/**
 * @description 语义模型导入项接口
 */
export interface SemanticModelImportItem {
  /** 表名 */
  tableName: string;
  /** 列名 */
  columnName: string;
  /** 业务名称 */
  businessName: string;
  /** 同义词 */
  synonyms?: string;
  /** 业务描述 */
  businessDescription?: string;
  /** 列注释 */
  columnComment?: string;
  /** 数据类型 */
  dataType: string;
}

/**
 * @description 批量导入语义模型的 DTO
 */
export interface SemanticModelBatchImportDTO {
  /** 智能体 ID */
  agentId: number;
  /** 导入项列表 */
  items: SemanticModelImportItem[];
}

/**
 * @description 批量导入结果接口
 */
export interface BatchImportResult {
  /** 总条数 */
  total: number;
  /** 成功条数 */
  successCount: number;
  /** 失败条数 */
  failCount: number;
  /** 错误信息列表 */
  errors: string[];
}

const API_BASE_URL = '/api/semantic-model';

/**
 * @description 语义模型业务逻辑处理类
 */
class SemanticModelService {
  /**
   * @description 获取语义模型列表
   * @param {number} [agentId] - 关联的智能体 ID
   * @param {string} [keyword] - 搜索关键词
   * @returns {Promise<SemanticModel[]>} 语义模型列表
   */
  async list(agentId?: number, keyword?: string): Promise<SemanticModel[]> {
    const params: { agentId?: string; keyword?: string } = {};
    if (agentId !== undefined) params.agentId = agentId.toString();
    if (keyword) params.keyword = keyword;

    const response = await axios.get<ApiResponse<SemanticModel[]>>(API_BASE_URL, { params });
    return response.data.data || [];
  }

  /**
   * @description 根据 ID 获取语义模型详情
   * @param {number} id - 语义模型 ID
   * @returns {Promise<SemanticModel | null>} 语义模型详情
   */
  async get(id: number): Promise<SemanticModel | null> {
    try {
      const response = await axios.get<ApiResponse<SemanticModel>>(`${API_BASE_URL}/${id}`);
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * @description 创建新语义模型
   * @param {SemanticModelAddDto} model - 模型信息
   * @returns {Promise<boolean>} 是否创建成功
   */
  async create(model: SemanticModelAddDto): Promise<boolean> {
    const response = await axios.post<ApiResponse>(API_BASE_URL, model);
    return response.data.success;
  }

  /**
   * @description 更新语义模型信息
   * @param {number} id - 语义模型 ID
   * @param {SemanticModel} model - 更新后的模型对象
   * @returns {Promise<boolean>} 是否更新成功
   */
  async update(id: number, model: SemanticModel): Promise<boolean> {
    try {
      const response = await axios.put<ApiResponse>(`${API_BASE_URL}/${id}`, model);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * @description 删除指定语义模型
   * @param {number} id - 语义模型 ID
   * @returns {Promise<boolean>} 是否删除成功
   */
  async delete(id: number): Promise<boolean> {
    try {
      const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/${id}`);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * @description 批量删除语义模型
   * @param {number[]} ids - 语义模型 ID 列表
   * @returns {Promise<boolean>} 是否全部删除成功
   */
  async batchDelete(ids: number[]): Promise<boolean> {
    const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/batch`, {
      data: ids,
    });
    return response.data.success;
  }

  /**
   * @description 批量启用语义模型
   * @param {number[]} ids - 语义模型 ID 列表
   * @returns {Promise<boolean>} 是否全部启用成功
   */
  async enable(ids: number[]): Promise<boolean> {
    const response = await axios.put<ApiResponse>(`${API_BASE_URL}/enable`, ids);
    return response.data.success;
  }

  /**
   * @description 批量禁用语义模型
   * @param {number[]} ids - 语义模型 ID 列表
   * @returns {Promise<boolean>} 是否全部禁用成功
   */
  async disable(ids: number[]): Promise<boolean> {
    const response = await axios.put<ApiResponse>(`${API_BASE_URL}/disable`, ids);
    return response.data.success;
  }

  /**
   * @description 批量导入语义模型
   * @param {SemanticModelBatchImportDTO} dto - 导入数据
   * @returns {Promise<BatchImportResult>} 导入结果统计
   */
  async batchImport(dto: SemanticModelBatchImportDTO): Promise<BatchImportResult> {
    const response = await axios.post<ApiResponse<BatchImportResult>>(
      `${API_BASE_URL}/batch-import`,
      dto,
    );
    return response.data.data || { total: 0, successCount: 0, failCount: 0, errors: [] };
  }

  /**
   * @description 通过 Excel 文件导入语义模型
   * @param {File} file - Excel 文件对象
   * @param {number} agentId - 智能体 ID
   * @returns {Promise<BatchImportResult>} 导入结果统计
   */
  async importExcel(file: File, agentId: number): Promise<BatchImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('agentId', agentId.toString());

    const response = await axios.post<ApiResponse<BatchImportResult>>(
      `${API_BASE_URL}/import/excel`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    );
    return response.data.data || { total: 0, successCount: 0, failCount: 0, errors: [] };
  }

  /**
   * @description 下载语义模型导入模板 Excel
   * @returns {Promise<void>}
   */
  async downloadTemplate(): Promise<void> {
    const response = await axios.get(`${API_BASE_URL}/template/download`, {
      responseType: 'blob',
    });

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'semantic_model_template.xlsx');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }
}

export default new SemanticModelService();
