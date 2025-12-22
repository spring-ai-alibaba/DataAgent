# Spring AI Alibaba DataAgent

（ "DataAgent用户1群"群的钉钉群号： 154405001431）

## 📖 项目简介

这是一个基于Spring AI Alibaba Graph的企业级智能数据分析 Agent。它不仅是 Text-to-SQL 转换器，更是一个具备支持 Python 深度分析与报告生成的 AI 虚拟数据分析师。

系统采用高度可扩展的架构设计，**全面兼容 OpenAI 接口规范**的对话模型与 Embedding 模型，并支持**灵活挂载任意向量数据库**。无论是私有化部署还是接入主流大模型服务，都能轻松适配，为企业提供灵活、可控的数据洞察服务。

同时，本项目可以支持**发布成MCP服务器**，具体看 [高级功能文档](docs/ADVANCED_FEATURES.md#mcp服务器)。

## ✨ 核心特性

- 🤖 **智能数据分析**: 自然语言转SQL，支持复杂查询和多轮对话
- 🐍 **Python深度分析**: 支持Python代码生成与执行，进行高级数据分析
- 📊 **智能报告生成**: 自动生成HTML/Markdown格式的分析报告
- 🔄 **人工反馈机制**: 支持人工审核和调整分析计划
- 🧠 **RAG检索增强**: 基于向量数据库的知识检索和语义理解
- 🎯 **多模型调度**: 灵活配置和切换不同的LLM和Embedding模型
- 🔌 **MCP服务器**: 支持作为MCP服务器对外提供服务
- 🔐 **API Key管理**: 支持API Key生成和权限管理

## 🚀 快速开始

详细的安装和配置指南请参考 [快速开始文档](docs/QUICK_START.md)。

### 基本步骤

1. **准备业务数据库** - 导入测试数据
2. **配置管理数据库** - 配置MySQL连接
3. **配置模型** - 添加Chat模型和Embedding模型
4. **启动服务** - 启动管理端和前端
5. **创建智能体** - 配置数据源和知识库

## 📚 文档导航

- [快速开始](docs/QUICK_START.md) - 环境配置、安装部署、系统体验
- [架构设计](docs/ARCHITECTURE.md) - 系统架构、核心能力、技术实现
- [开发者文档](docs/DEVELOPER_GUIDE.md) - 贡献指南、开发规范、测试指南
- [高级功能](docs/ADVANCED_FEATURES.md) - API调用、MCP服务器、自定义配置

## 🏗️ 项目结构

```
spring-ai-alibaba-data-agent/
├── data-agent-management    # 管理端（可直接启动的Web应用）
└── data-agent-frontend      # 前端代码 
```

## 🛠️ 技术栈

- **后端框架**: Spring Boot + Spring AI Alibaba
- **前端框架**: React + TypeScript
- **数据库**: MySQL + 向量数据库（支持多种）
- **AI模型**: 兼容OpenAI接口的各类大模型
- **执行环境**: Docker / 本地Python环境

## 🤝 如何贡献

我们欢迎社区的贡献！如果你想为本项目做出贡献，请查看我们的[开发者文档](docs/DEVELOPER_GUIDE.md)和[贡献指南](./CONTRIBUTING-zh.md)。

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

## 🔗 相关链接

- [Spring AI Alibaba 文档](https://springdoc.cn/spring-ai/)
- [钉钉交流群](https://qr.dingtalk.com/action/joingroup?code=v1,k1,your_group_code)

---

**注意**: README不是最新的，以最新代码为主。
