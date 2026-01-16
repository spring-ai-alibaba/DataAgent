<div align="center">
  <p><a href="./README.md">中文</a> | English</p>
  <h1>Spring AI Alibaba DataAgent</h1>
  <p>
    <strong>Enterprise-grade Intelligent Data Analyst powered by <a href="https://github.com/alibaba/spring-ai-alibaba" target="_blank">Spring AI Alibaba</a></strong>
  </p>
  <p>
     Text-to-SQL | Python Deep Analysis | Intelligent Reports | MCP Server | RAG Enhancement
  </p>

  <p>
    <a href="https://github.com/alibaba/spring-ai-alibaba"><img src="https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.0.0-blue" alt="Spring AI Alibaba"></a>
    <img src="https://img.shields.io/badge/Spring%20Boot-3.4.8+-green" alt="Spring Boot">
    <img src="https://img.shields.io/badge/Java-17+-orange" alt="Java">
    <img src="https://img.shields.io/badge/License-Apache%202.0-red" alt="License">
    <a href="https://deepwiki.com/spring-ai-alibaba/DataAgent"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki"></a>
  </p>

   <p>
    <a href="#-introduction">Introduction</a> •
    <a href="#-core-features">Core Features</a> •
    <a href="#-quick-start">Quick Start</a> •
    <a href="#-documentation">Documentation</a> •
    <a href="#-community--contribution">Community</a>
  </p>
</div>

<br/>

<div align="center">
    <img src="img/LOGO.png" alt="DataAgent" width="1807" style="border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);">
</div>

<br/>

## Introduction

**DataAgent** is an enterprise-grade intelligent data analysis Agent built on **Spring AI Alibaba Graph**. It goes beyond traditional Text-to-SQL tools, evolving into an AI-powered data analyst capable of executing **Python deep analysis** and generating **multi-dimensional chart reports**.

The system adopts a highly extensible architecture design, **fully compatible with OpenAI API specifications** for chat models and embedding models, and supports **flexible integration with any vector database**. Whether for private deployment or integration with mainstream LLM services (such as Qwen, Deepseek), it can be easily adapted to provide flexible and controllable data insight services for enterprises.

Additionally, this project natively supports **MCP (Model Context Protocol)**, enabling seamless integration as an MCP server into MCP-compatible ecosystem tools such as Claude Desktop.

## Core Features

| Feature | Description |
| :--- | :--- |
| **Intelligent Data Analysis** | StateGraph-based Text-to-SQL conversion, supporting complex multi-table queries and multi-turn conversation intent understanding. |
| **Python Deep Analysis** | Built-in Docker/Local Python executor, automatically generating and executing Python code for statistical analysis and machine learning predictions. |
| **Intelligent Report Generation** | Analysis results are automatically summarized into HTML/Markdown reports with ECharts visualizations, WYSIWYG. |
| **Human Feedback Mechanism** | Innovative Human-in-the-loop mechanism, supporting user intervention and adjustments during the plan generation phase. |
| **RAG Retrieval Enhancement** | Integrated vector database, supporting semantic retrieval of business metadata and terminology libraries to improve SQL generation accuracy. |
| **Multi-Model Orchestration** | Built-in model registry, supporting runtime dynamic switching between different LLM and Embedding models. |
| **MCP Server** | Compliant with MCP protocol, supporting external provision of NL2SQL and agent management capabilities as a Tool Server. |
| **API Key Management** | Comprehensive API Key lifecycle management with fine-grained permission control. |

## Project Structure

![dataagent-structure](img/dataagent-structure.png)


## Quick Start

> For detailed installation and configuration guide, please refer to [Quick Start Guide](docs/QUICK_START.md).

### 1. Prerequisites
- JDK 17+
- MySQL 5.7+
- Node.js 16+

### 2. Start Services

```bash
# 1. Import database
mysql -u root -p < data-agent-management/src/main/resources/sql/schema.sql

# 2. Start backend
cd data-agent-management
./mvnw spring-boot:run

# 3. Start frontend
cd data-agent-frontend
npm install && npm run dev
```

### 3. Access the System
Open your browser and visit `http://localhost:3000` to start creating your first data agent!

## Documentation

| Document | Contents |
| :--- | :--- |
| [Quick Start](docs/QUICK_START.md) | Environment requirements, database import, basic configuration, getting started |
| [Architecture Design](docs/ARCHITECTURE.md) | System layered architecture, StateGraph and workflow design, core module sequence diagrams |
| [Developer Guide](docs/DEVELOPER_GUIDE.md) | Development environment setup, detailed configuration manual, coding standards, extension development (vector DB/models) |
| [Advanced Features](docs/ADVANCED_FEATURES.md) | API Key invocation, MCP server configuration, custom hybrid retrieval strategies, Python executor configuration |
| [Knowledge Configuration Best Practices](docs/KNOWLEDGE_USAGE.md) | Explanation and usage of semantic models, business knowledge, and agent knowledge |

## Community & Contribution

- **DingTalk Group**: `154405001431` ("DataAgent User Group 1") Some users may not be able to join due to account security issues. If possible, please try with a different account.
- **Contribution Guide**: Community contributions are welcome! Please refer to the [Developer Guide](docs/DEVELOPER_GUIDE.md) to learn how to submit PRs.

## License

This project is licensed under the Apache License 2.0.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=spring-ai-alibaba/DataAgent&type=Date)](https://star-history.com/#spring-ai-alibaba/DataAgent&Date)

## Contributors

<a href="https://github.com/spring-ai-alibaba/DataAgent/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=spring-ai-alibaba/DataAgent" />
</a>

---

<div align="center">
    Made with ❤️ by Spring AI Alibaba DataAgent Team
</div>
