# 快速开始

本文档将指导您完成 DataAgent 的安装、配置和首次运行。

## 📋 环境要求

- **JDK**: 17 或更高版本
- **MySQL**: 5.7 或更高版本
- **Node.js**: 16 或更高版本
- **Docker**: (可选) 用于Python代码执行
- **向量数据库**: (可选) 默认使用内存向量库

## 🗄️ 1. 业务数据库准备

可以在项目仓库获取测试表和数据：

文件在：`data-agent-management/src/main/resources/sql`，里面有4个文件：
- `schema.sql` - 功能相关的表结构
- `data.sql` - 功能相关的数据
- `product_schema.sql` - 模拟数据表结构
- `product_data.sql` - 模拟数据

将表和数据导入到你的MySQL数据库中。

```bash
# 示例：使用 MySQL 命令行导入
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/schema.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/data.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/product_schema.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/product_data.sql
```

## ⚙️ 2. 配置

### 2.1 配置management数据库

在`data-agent-management/src/main/resources/application.yml`中配置你的MySQL数据库连接信息。

> 初始化行为说明：默认开启自动创建表并插入示例数据（`spring.sql.init.mode: always`）。生产环境建议关闭，避免示例数据回填覆盖你的业务数据。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/saa_data_agent?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
```

### 2.2 数据初始化配置

默认开启自动初始化 (`spring.sql.init.mode: always`)。

> 关于如何关闭自动初始化，请参考 [开发者指南 - 数据库初始化配置](DEVELOPER_GUIDE.md#8-数据库初始化配置-database-initialization)。

### 2.3 配置模型

> 如果涉及手动管理模型依赖（非默认 Starter），请参考 [开发者指南 - 扩展依赖配置](DEVELOPER_GUIDE.md#9-扩展依赖配置-dependency-extension)。

启动项目，点击模型配置，新增模型填写自己的apikey即可。

![add-model.png](../img/add-model.png)

注意，如Qwen，OpenAi,Deepseek,Siliconflow(硅基流动) 等兼容Open Ai的厂商不需要更改Completions 路径和Embedding路径。
目前项目的对话模型和向量模型都统一OpenAI 接口规范接入，模型的接入配置如Ollama参考spring ai 文档的OpenAI API 兼容性章节
如果是自己部署的模型，baseurl和completions-path就是完整的chat模型地址，向量模型同理。

### 2.4 嵌入模型批处理策略配置

> 详细配置参数请参考 [开发者指南 - 开发配置手册](DEVELOPER_GUIDE.md#⚙️-开发配置手册)。

### 2.5 向量库配置

系统默认使用内存向量库，同时系统提供了对es的混合检索支持。

#### 2.5.1 向量库依赖引入

您可以自行引入你想要的持久化向量库，只需要往ioc容器提供一个org.springframework.ai.vectorstore.VectorStore类型的bean即可。例如直接引入PGvector的starter

```xml
<dependency>
	<groupId>org.springframework.ai</groupId>
	<artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

详细对应的向量库参考文档：https://springdoc.cn/spring-ai/api/vectordbs.html

#### 2.5.2 向量库schema设置

以下为es的schema结构，其他向量库如milvus，pg等自行可根据如下的es的结构建立自己的schema。尤其要注意metadata中的每个字段的数据类型。

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

#### 2.5.3 向量库配置参数

> 详细配置参数请参考 [开发者指南 - 开发配置手册](DEVELOPER_GUIDE.md#⚙️-开发配置手册)。

### 2.6 检索融合策略

> 详细配置参数请参考 [开发者指南 - 开发配置手册](DEVELOPER_GUIDE.md#⚙️-开发配置手册)。

### 2.7 替换vector-store的实现类

> 关于如何替换默认的内存向量库（如使用 PGVector, Milvus 等），请参考 [开发者指南 - 扩展依赖配置](DEVELOPER_GUIDE.md#9-扩展依赖配置-dependency-extension)。

## 🚀 3. 启动管理端

在`data-agent-management`目录下，运行 `DataAgentApplication.java` 类。

```bash
cd data-agent-management
./mvnw spring-boot:run
```

或者在IDE中直接运行 `DataAgentApplication.java`。

## 🌐 4. 启动WEB页面

进入 `data-agent-frontend` 目录

### 4.1 安装依赖

```bash
# 使用 npm
npm install

# 或使用 yarn
yarn install
```

### 4.2 启动服务

```bash
# 使用 npm
npm run dev

# 或使用 yarn
yarn dev
```

启动成功后，访问地址 http://localhost:3000

## 🎯 5. 系统体验

### 5.1 数据智能体的创建与配置

访问 http://localhost:3000 ，可以看到当前项目的智能体列表（默认有四个占位智能体，并没有对接数据，可以删除掉然后创建新的智能体）

![homepage-agents.png](../img/homepage-agents.png)

点击右上角"创建智能体" ，这里只需要输入智能体名称，其他配置都选默认。

![agent-create.png](../img/agent-create.png)

创建成功后，可以看到智能体配置页面。

![agent-config.png](../img/agent-config.png)

#### 配置数据源

进入数据源配置页面，配置业务数据库（我们在环境初始化时第一步提供的业务数据库）。

![datasource-config.png](../img/datasource-config.png)

添加完成后，可以在列表页面验证数据源连接是否正常。

![datasource-validation.png](../img/datasource-validation.png)

对于添加的新数据源，需要选择使用哪些数据表进行数据分析。

![datasource-tables.png](../img/datasource-tables.png)

之后点击右上角的"初始化数据源"按钮。

![datasource-init.png](../img/datasource-init.png)

#### 配置预设问题

预设问题管理，可以为智能体设置预设问题

![preset-questions.png](../img/preset-questions.png)

#### 配置语义模型

语义模型管理，可以为智能体设置语义模型。
语义模型库定义业务术语到数据库物理结构的精确转换规则，存储的是字段名的映射关系。
例如`customerSatisfactionScore`对应数据库中的`csat_score`字段。

![semantic-models.png](../img/semantic-models.png)

#### 配置业务知识

业务知识管理，可以为智能体设置业务知识。
业务知识定义了业务术语和业务规则，比如GMV= 商品交易总额,包含付款和未付款的订单金额。
业务知识可以设置为召回或者不召回，配置完成后需要点击右上角的"同步到向量库"按钮。

![business-knowledge.png](../img/business-knowledge.png)

成功后可以点击"前往运行界面"使用智能体进行数据查询。 调试没问题后，可以发布智能体。

> 目前"智能体知识"和"访问API"当前版本暂未实现。

### 5.2 数据智能体的运行

运行界面

![run-page.png](../img/run-page.png)

运行界面左侧是历史消息记录，右侧是当前会话记录、输入框以及请求参数配置。

输入框中输入问题，点击"发送"按钮，即可开始查询。

![analyze-question.png](../img/analyze-question.png)

分析报告为HTML格式报告，点击"下载报告"按钮，即可下载最终报告。

![analyze-result.png](../img/analyze-result.png)

#### 运行模式

除了默认的请求模式，智能体运行时还支持"人工反馈"，"仅NL2SQL"，"简洁报告"和"显示SQL运行结果"等模式。

**默认模式**

默认情况不开启人工反馈模式，智能体直接自动生成计划并执行，并对SQL执行结果进行解析，生成报告。

**人工反馈模式**

如果开启人工反馈模式，则智能体会在生成计划后，等待用户确认，然后根据用户选择的反馈结果，更改计划或者执行计划。

![feedback-mode.png](../img/feedback-mode.png)

**仅NL2SQL模式**

"仅NL2SQL模式"会让智能体只生成SQL和运行获取结果，不会生成报告。

![nl2sql-mode.png](../img/nl2sql-mode.png)

**显示SQL运行结果**

"显示SQL运行结果"会在生成SQL和运行获取结果后，将SQL运行结果展示给用户。

![show-sql-result.png](../img/show-sql-result.png)


## 📚 下一步

- 了解[架构设计](ARCHITECTURE.md)以深入理解系统原理
- 查看[高级功能](ADVANCED_FEATURES.md)了解更多高级特性
- 阅读[开发者文档](DEVELOPER_GUIDE.md)参与项目贡献
