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
 * @description 结果集数据结构定义，用于统一处理表格、图表等展示数据
 */

/**
 * @description 结果数据包装接口
 */
export interface ResultData {
  /** 显示样式配置 */
  displayStyle?: ResultDisplayStyleBO;
  /** 结果集数据 */
  resultSet: ResultSetData;
}

/**
 * @description 结果显示样式业务对象
 */
export interface ResultDisplayStyleBO {
  /** 图表类型 (如 bar, line, table) */
  type: string;
  /** 标题 */
  title: string;
  /** X 轴字段名 */
  x: string;
  /** Y 轴字段名列表 */
  y: Array<string>;
}

/**
 * @description 结果集核心数据结构
 */
export interface ResultSetData {
  /** 列名列表 */
  column: string[];
  /** 数据行列表，每行为字段名到值的映射 */
  data: Array<Record<string, string>>;
  /** 错误信息 */
  errorMsg?: string;
}

/**
 * @description 分页配置接口
 */
export interface PaginationConfig {
  /** 当前页码 */
  currentPage: number;
  /** 每页条数 */
  pageSize: number;
  /** 总条数 */
  total: number;
}

/**
 * @description 结果集显示配置接口
 */
export interface ResultSetDisplayConfig {
  /** 是否显示 SQL 执行结果 */
  showSqlResults: boolean;
  /** 默认每页条数 */
  pageSize: number;
}
