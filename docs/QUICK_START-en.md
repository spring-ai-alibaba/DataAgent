[中文](./QUICK_START.md) | English

# Quick Start

This document will guide you through the installation, configuration, and first run of DataAgent.

## Prerequisites

- **JDK**: 17 or higher
- **MySQL**: 5.7 or higher
- **Node.js**: 16 or higher
- **Docker**: (Optional) For Python code execution
- **Vector Database**: (Optional) Uses in-memory vector store by default

## 1. Business Database Preparation

You can get test tables and data from the project repository:

Files are located in: `data-agent-management/src/main/resources/sql`, which contains 4 files:
- `schema.sql` - Table structure for features
- `data.sql` - Data for features
- `product_schema.sql` - Sample data table structure
- `product_data.sql` - Sample data

Import the tables and data into your MySQL database.

```bash
# Example: Import using MySQL command line
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/schema.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/data.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/product_schema.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/product_data.sql
```

## 2. Configuration

### 2.1 Configure Management Database

Configure your MySQL database connection in `data-agent-management/src/main/resources/application.yml`.

> Initialization Behavior: Auto table creation and sample data insertion is enabled by default (`spring.sql.init.mode: always`). For production environments, it's recommended to disable this to avoid sample data overwriting your business data.

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/saa_data_agent?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
```

### 2.2 Data Initialization Configuration

Auto initialization is enabled by default (`spring.sql.init.mode: always`).

> For information on how to disable auto initialization, please refer to [Developer Guide - Database Initialization Configuration](DEVELOPER_GUIDE-en.md#8-database-initialization).

### 2.3 Configure Model

> If you need to manually manage model dependencies (not using default Starter), please refer to [Developer Guide - Dependency Extension Configuration](DEVELOPER_GUIDE-en.md#9-dependency-extension).

Start the project, click on Model Configuration, add a new model and fill in your API key.

![add-model.png](../img/add-model.png)

1. Standard Provider Integration: If you're using a built-in supported AI provider (like OpenAI, Deepseek, etc.), you usually only need to provide the Model Name and API Key.

2. Custom and Local Model Integration (Ollama/Self-hosted Gateway): This system is based on Spring AI architecture and supports the standard OpenAI interface protocol. If you're connecting to Ollama or other custom gateways, please note the following:

	- Protocol Compatibility: Please refer to the Spring AI official documentation about OpenAI compatibility to ensure your gateway response format meets the standard.

	- Address Configuration: For self-deployed models, please accurately fill in the base-url and completions-path. The system will concatenate them into the complete call address, for example: http://localhost:11434/v1/chat/completions

3. Troubleshooting: If the configuration doesn't work after setup, we recommend first using Postman to test your interface address to confirm network connectivity and parameter format are correct.


### 2.4 Embedding Model Batch Processing Strategy Configuration

> For detailed configuration parameters, please refer to [Developer Guide - Development Configuration Manual](DEVELOPER_GUIDE-en.md#development-configuration-manual).

### 2.5 Vector Store Configuration

The system uses an in-memory vector store by default, and also provides hybrid search support for Elasticsearch.

#### 2.5.1 Vector Store Dependency Import

You can import your preferred persistent vector store. You just need to provide a bean of type org.springframework.ai.vectorstore.VectorStore to the IoC container. For example, directly import the PGvector starter:

```xml
<dependency>
	<groupId>org.springframework.ai</groupId>
	<artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

For detailed vector store documentation, refer to: https://springdoc.cn/spring-ai/api/vectordbs.html

#### 2.5.2 Vector Store Schema Setup

Below is the ES schema structure. For other vector stores like Milvus, PG, etc., you can create your own schema based on this ES structure. Pay special attention to the data type of each field in metadata.

```json
{
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": true,
        "similarity": "cosine",
        "index_options": {
          "type": "int8_hnsw",
          "m": 16,
          "ef_construction": 100
        }
      },
      "id": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "metadata": {
        "properties": {
          "agentId": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          },
          "agentKnowledgeId": {
            "type": "long"
          },
          "businessTermId": {
            "type": "long"
          },
          "concreteAgentKnowledgeType": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          },
          "vectorType": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          }
        }
      }
    }
  }
}
```

#### 2.5.3 Vector Store Configuration Parameters

> For detailed configuration parameters, please refer to [Developer Guide - Development Configuration Manual](DEVELOPER_GUIDE-en.md#development-configuration-manual).

### 2.6 Retrieval Fusion Strategy

> For detailed configuration parameters, please refer to [Developer Guide - Development Configuration Manual](DEVELOPER_GUIDE-en.md#development-configuration-manual).

### 2.7 Replace Vector Store Implementation

> For information on how to replace the default in-memory vector store (e.g., using PGVector, Milvus, etc.), please refer to [Developer Guide - Dependency Extension Configuration](DEVELOPER_GUIDE-en.md#9-dependency-extension).

## 3. Start Management Backend

In the `data-agent-management` directory, run the `DataAgentApplication.java` class.

```bash
cd data-agent-management
./mvnw spring-boot:run
```

Or run `DataAgentApplication.java` directly in your IDE.

## 4. Start Web Frontend

Navigate to the `data-agent-frontend` directory

### 4.1 Install Dependencies

```bash
# Using npm
npm install

# Or using yarn
yarn install
```

### 4.2 Start Service

```bash
# Using npm
npm run dev

# Or using yarn
yarn dev
```

After successful startup, access http://localhost:3000

## 5. System Experience

### 5.1 Creating and Configuring Data Agent

Visit http://localhost:3000 to see the current list of agents (there are four placeholder agents by default that are not connected to data; you can delete them and create new agents)

![homepage-agents.png](../img/homepage-agents.png)

Click "Create Agent" in the upper right corner. Here you only need to enter the agent name, and use default settings for other configurations.

![agent-create.png](../img/agent-create.png)

After creation, you can see the agent configuration page.

![agent-config.png](../img/agent-config.png)

#### Configure Data Source

Go to the data source configuration page and configure the business database (the business database we provided in the first step of environment initialization).

![datasource-config.png](../img/datasource-config.png)

After adding, you can verify the data source connection on the list page.

![datasource-validation.png](../img/datasource-validation.png)

For newly added data sources, you need to select which data tables to use for data analysis.

![datasource-tables.png](../img/datasource-tables.png)

Then click the "Initialize Data Source" button in the upper right corner.

![datasource-init.png](../img/datasource-init.png)

#### Configure Preset Questions

Preset question management allows you to set preset questions for the agent.

![preset-questions.png](../img/preset-questions.png)

#### Configure Semantic Model

Semantic model management allows you to set semantic models for the agent.
The semantic model library defines precise conversion rules from business terms to database physical structures, storing field name mappings.
For example, `customerSatisfactionScore` corresponds to the `csat_score` field in the database.

![semantic-models.png](../img/semantic-models.png)

#### Configure Business Knowledge

Business knowledge management allows you to set business knowledge for the agent.
Business knowledge defines business terms and business rules, such as GMV = Gross Merchandise Volume, including paid and unpaid order amounts.
Business knowledge can be set to recall or not recall. After configuration, click the "Sync to Vector Store" button in the upper right corner.

![business-knowledge.png](../img/business-knowledge.png)

After success, you can click "Go to Run Interface" to use the agent for data queries. After debugging is complete, you can publish the agent.

> Note: "Access API" is not fully implemented in the current version and is reserved for secondary development.

### 5.2 Running the Data Agent

Run Interface

![run-page.png](../img/run-page.png)

The left side of the run interface shows historical message records, and the right side shows current session records, input box, and request parameter configuration.

Enter a question in the input box and click the "Send" button to start querying.

![analyze-question.png](../img/analyze-question.png)

The analysis report is in HTML format. Click the "Download Report" button to download the final report.

![analyze-result.png](../img/analyze-result.png)

#### Run Modes

Besides the default request mode, the agent runtime also supports "Human Feedback", "NL2SQL Only", "Concise Report", and "Show SQL Results" modes.

**Default Mode**

By default, human feedback mode is not enabled. The agent automatically generates and executes the plan, parses SQL execution results, and generates a report.

**Human Feedback Mode**

If human feedback mode is enabled, the agent will wait for user confirmation after generating the plan, then modify or execute the plan based on the user's selected feedback result.

![feedback-mode.png](../img/feedback-mode.png)

**NL2SQL Only Mode**

"NL2SQL Only Mode" makes the agent only generate SQL and retrieve results without generating a report.

![nl2sql-mode.png](../img/nl2sql-mode.png)

**Show SQL Results**

"Show SQL Results" displays the SQL execution results to the user after generating SQL and retrieving results.

![show-sql-result.png](../img/show-sql-result.png)


## Next Steps

- Learn about [Architecture Design](ARCHITECTURE-en.md) to understand the system principles in depth
- Check [Advanced Features](ADVANCED_FEATURES-en.md) to learn about more advanced features
- Read [Developer Documentation](DEVELOPER_GUIDE-en.md) to contribute to the project
