<div align="center">
  <h1>Spring AI Alibaba DataAgent</h1>
  <p>
    <strong>基于 [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) 的企业级智能数据分析师</strong>
  </p>
  <p>
     Text-to-SQL | Python 深度分析 | 智能报告 | MCP 服务器 | RAG 增强
  </p>

  <p>
    <a href="https://github.com/alibaba/spring-ai-alibaba"><img src="https://img.shields.io/badge/Spring%20AI%20Alibaba-1.0.4-blue" alt="Spring AI Alibaba"></a>
    <img src="https://img.shields.io/badge/Spring%20Boot-3.2+-green" alt="Spring Boot">
    <img src="https://img.shields.io/badge/Java-17+-orange" alt="Java">
    <img src="https://img.shields.io/badge/License-Apache%202.0-red" alt="License">
  </p>

   <p>
    <a href="#-项目简介">项目简介</a> • 
    <a href="#-核心特性">核心特性</a> • 
    <a href="#-快速开始">快速开始</a> • 
    <a href="#-文档导航">文档导航</a> • 
    <a href="#-加入社区">加入社区</a>
  </p>
</div>

<br/>

<div align="center">
    <img src="img/LOGO.png" alt="DataAgent" width="1807" style="border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);">
</div>

<br/>

## 📖 项目简介

**DataAgent** 是一个基于 **Spring AI Alibaba Graph** 打造的企业级智能数据分析 Agent。它超越了传统的 Text-to-SQL 工具，进化为一个能够执行 **Python 深度分析**、生成 **多维度图表报告** 的 AI 智能数据分析师。

系统采用高度可扩展的架构设计，**全面兼容 OpenAI 接口规范**的对话模型与 Embedding 模型，并支持**灵活挂载任意向量数据库**。无论是私有化部署还是接入主流大模型服务（如 Qwen, Deepseek），都能轻松适配，为企业提供灵活、可控的数据洞察服务。

同时，本项目原生支持 **MCP (Model Context Protocol)**，可作为 MCP 服务器无缝集成到 Claude Desktop 等支持 MCP 的生态工具中。

## ✨ 核心特性

| 特性 | 说明 |
| :--- | :--- |
| **智能数据分析** | 基于 StateGraph 的 Text-to-SQL 转换，支持复杂的多表查询和多轮对话意图理解。 |
| **Python 深度分析** | 内置 Docker/Local Python 执行器，自动生成并执行 Python 代码进行统计分析与机器学习预测。 |
| **智能报告生成** | 分析结果自动汇总为包含 ECharts 图表的 HTML/Markdown 报告，所见即所得。 |
| **人工反馈机制** | 独创的 Human-in-the-loop 机制，支持用户在计划生成阶段进行干预和调整。 |
| **RAG 检索增强** | 集成向量数据库，支持对业务元数据、术语库的语义检索，提升 SQL生成准确率。 |
| **多模型调度** | 内置模型注册表，支持运行时动态切换不同的 LLM 和 Embedding 模型。 |
| **MCP 服务器** | 遵循 MCP 协议，支持作为 Tool Server 对外提供 NL2SQL 和 智能体管理能力。 |
| **API Key 管理** | 完善的 API Key 生命周期管理，支持细粒度的权限控制。 |

## 🏗️ 项目结构

![dataagent-structure](img/dataagent-structure.png)


## 🚀 快速开始

> 详细的安装和配置指南请参考 [📑 快速开始文档](docs/QUICK_START.md)。

### 1. 准备环境
- JDK 17+
- MySQL 5.7+
- Node.js 16+

### 2. 启动服务

```bash
# 1. 导入数据库
mysql -u root -p < data-agent-management/src/main/resources/sql/schema.sql

# 2. 启动后端
cd data-agent-management
./mvnw spring-boot:run

# 3. 启动前端
cd data-agent-frontend
npm install && npm run dev
```

### 3. 访问系统
打开浏览器访问 `http://localhost:3000`，开始创建您的第一个数据智能体！

## 📚 文档导航

| 文档 | 此文档包含的内容 |
| :--- | :--- |
| [快速开始](docs/QUICK_START.md) | 环境要求、数据库导入、基础配置、系统初体验 |
| [架构设计](docs/ARCHITECTURE.md) | 系统分层架构、StateGraph与工作流设计、核心模块时序图 |
| [开发者指南](docs/DEVELOPER_GUIDE.md) | 开发环境搭建、详细配置手册、代码规范、扩展开发(向量库/模型) |
| [高级功能](docs/ADVANCED_FEATURES.md) | API Key 调用、MCP 服务器配置、自定义混合检索策略、Python执行器配置 |
| [知识配置最佳实践](docs/KNOWLEDGE_USAGE.md) | 语义模型，业务知识，智能体知识的解释和使用 |

## 🤝 加入社区 & 贡献

- **钉钉交流群**: `154405001431` ("DataAgent用户1群")
- **贡献指南**: 欢迎社区贡献！请查阅 [开发者文档](docs/DEVELOPER_GUIDE.md) 了解如何提交 PR。

## 📄 许可证

本项目采用 Apache License 2.0 许可证。
## Star 历史

[![Star History Chart](https://api.star-history.com/svg?repos=spring-ai-alibaba/DataAgent&type=Date)](https://star-history.com/#spring-ai-alibaba/DataAgent&Date)

## 贡献者名单

<a href="https://github.com/spring-ai-alibaba/DataAgent/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=spring-ai-alibaba/DataAgent" />
</a>

---
<div align="center">
    Made with ❤️ by Spring AI Alibaba DataAgent Team
</div>
