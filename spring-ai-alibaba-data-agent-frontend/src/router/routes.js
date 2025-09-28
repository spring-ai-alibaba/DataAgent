/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// 路由模块化配置
const routes = [
  // 首页重定向
  {
    path: '/',
    redirect: '/agents'
  },

  // 智能体管理模块
  {
    path: '/agents',
    name: 'AgentList',
    component: () => import('@/views/AgentList.vue'),
    meta: {
      title: '智能体列表',
      module: 'agent'
    }
  },
  {
    path: '/agent/create',
    name: 'CreateAgent',
    component: () => import('@/views/CreateAgent.vue'),
    meta: {
      title: '创建智能体',
      module: 'agent'
    }
  },
  {
    path: '/agent/:id',
    name: 'AgentDetail',
    component: () => import('@/views/AgentDetail.vue'),
    meta: {
      title: '智能体详情',
      module: 'agent'
    }
  },
  {
    path: '/agent/:id/run',
    name: 'AgentRun',
    component: () => import('@/views/AgentRun.vue'),
    meta: {
      title: '运行智能体',
      module: 'agent'
    }
  },
  {
    path: '/workspace',
    name: 'AgentWorkspace',
    component: () => import('@/views/AgentWorkspace.vue'),
    meta: {
      title: '智能体工作台',
      module: 'agent'
    }
  },

  // NL2SQL分析模块
  {
    path: '/nl2sql',
    name: 'NL2SQL',
    component: () => import('@/views/Home.vue'),
    meta: {
      title: 'NL2SQL转换',
      module: 'analysis'
    }
  },
  {
    path: '/home-full',
    name: 'HomeFull',
    component: () => import('@/views/Home.vue'),
    meta: {
      title: '完整版首页',
      module: 'analysis'
    }
  },

  // 业务知识管理模块
  {
    path: '/business-knowledge',
    name: 'BusinessKnowledge',
    component: () => import('@/views/BusinessKnowledgeRefactored.vue'),
    meta: {
      title: '业务知识管理',
      module: 'knowledge'
    }
  },

  // 语义模型配置模块
  {
    path: '/semantic-model',
    name: 'SemanticModel',
    component: () => import('@/views/SemanticModelRefactored.vue'),
    meta: {
      title: '语义模型配置',
      module: 'semantic'
    }
  },

  // 设计系统页面（开发调试用）
  {
    path: '/design-system',
    name: 'DesignSystem',
    component: () => import('@/views/DesignSystem.vue'),
    meta: {
      title: '设计系统',
      module: 'dev'
    }
  },

  // 404页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: {
      title: '页面未找到',
      module: 'error'
    }
  }
]

export default routes
