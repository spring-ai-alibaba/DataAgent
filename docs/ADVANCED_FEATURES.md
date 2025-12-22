# 高级功能使用

本文档介绍 DataAgent 的高级功能和自定义配置选项。

## 🔑 访问 API（API Key 调用）

> **注意**: 当前版本仅提供 API Key 生成、重置、删除与开关的管理能力，**尚未在后端对 `X-API-Key` 做权限校验**；需要鉴权的生产场景请自行在后端拦截器中补充校验逻辑后再对外开放。

### API Key 管理

1. 在智能体详情左侧菜单进入"访问 API"
2. 为智能体生成 Key，并根据需要启用/禁用
3. 调用会话接口时在请求头添加 `X-API-Key: <your_api_key>`

![访问 API Key](../img/apikey.png)

### API 调用示例

#### 创建会话

```bash
curl -X POST "http://127.0.0.1:3000/api/agent/<agentId>/sessions" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your_api_key>" \
  -d '{"title":"demo"}'
```

#### 发送消息

```bash
curl -X POST "http://127.0.0.1:3000/api/sessions/<sessionId>/messages" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your_api_key>" \
  -d '{"role":"user","content":"给我一个示例","messageType":"text"}'
```

### 实现自定义鉴权

如需在生产环境启用API Key鉴权，可以创建一个拦截器：

```java
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private AgentService agentService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws Exception {
        String apiKey = request.getHeader("X-API-Key");
        
        if (apiKey == null || apiKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        // 验证API Key
        boolean isValid = agentService.validateApiKey(apiKey);
        
        if (!isValid) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        
        return true;
    }
}
```

## 🔌 MCP服务器

DataAgent 支持作为 MCP (Model Context Protocol) 服务器对外提供服务。

### 配置说明

本项目通过 **Mcp Server Boot Starter** 实现MCP服务器功能。

更多详细配置请参考官方文档：
https://springdoc.cn/spring-ai/api/mcp/mcp-server-boot-starter-docs.html#_配置属性

### 端点配置

**默认配置**:
- MCP Web 传输的自定义 SSE 端点路径：`项目地址:项目端口/sse`
- 例如：`http://localhost:8065/sse`

**自定义端点**:

可通过配置修改端点路径：

```yaml
spring:
  ai:
    mcp:
      server:
        sse-endpoint: /custom-mcp-endpoint
```

### 可用工具

#### 1. nl2SqlToolCallback

将自然语言查询转换为SQL语句。

```json
{
  "name": "nl2SqlToolCallback",
  "description": "将自然语言查询转换为SQL语句。使用指定的智能体将用户的自然语言查询描述转换为可执行的SQL语句，支持复杂的数据查询需求。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "nl2SqlRequest": {
        "type": "object",
        "properties": {
          "agentId": {
            "type": "string",
            "description": "智能体ID，用于指定使用哪个智能体进行NL2SQL转换"
          },
          "naturalQuery": {
            "type": "string",
            "description": "自然语言查询描述，例如：'查询销售额最高的10个产品'"
          }
        },
        "required": ["agentId", "naturalQuery"]
      }
    },
    "required": ["nl2SqlRequest"],
    "additionalProperties": false
  }
}
```

**使用示例**:

```json
{
  "nl2SqlRequest": {
    "agentId": "agent-123",
    "naturalQuery": "查询过去30天销售额最高的10个产品"
  }
}
```

#### 2. listAgentsToolCallback

查询智能体列表，支持按状态和关键词过滤。

```json
{
  "name": "listAgentsToolCallback",
  "description": "查询智能体列表，支持按状态和关键词过滤。可以根据智能体的状态（如已发布PUBLISHED、草稿DRAFT等）进行过滤，也可以通过关键词搜索智能体的名称、描述或标签。返回按创建时间降序排列的智能体列表。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "agentListRequest": {
        "type": "object",
        "properties": {
          "keyword": {
            "type": "string",
            "description": "按关键词搜索智能体名称或描述"
          },
          "status": {
            "type": "string",
            "description": "按状态过滤，例如 '状态：draft-待发布，published-已发布，offline-已下线"
          }
        },
        "required": ["keyword", "status"]
      }
    },
    "required": ["agentListRequest"],
    "additionalProperties": false
  }
}
```

**使用示例**:

```json
{
  "agentListRequest": {
    "keyword": "销售",
    "status": "published"
  }
}
```

### 本地调试

使用 MCP Inspector 进行本地调试：

```bash
npx @modelcontextprotocol/inspector http://localhost:8065/mcp/connection
```

这将打开一个调试界面，可以测试MCP服务器的各项功能。

## 🗄️ 自定义向量库实现

### 使用内置向量库

系统默认使用内存向量库，适合开发和测试环境。

### 切换到持久化向量库

#### 1. PGVector

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        host: localhost
        port: 5432
        database: vector_db
        username: postgres
        password: postgres
        schema-name: public
        table-name: vector_store
        dimensions: 1024
```

#### 2. Milvus

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-milvus</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
spring:
  ai:
    vectorstore:
      milvus:
        host: localhost
        port: 19530
        database-name: default
        collection-name: vector_store
```

#### 3. Elasticsearch

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-elasticsearch</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
spring:
  ai:
    vectorstore:
      elasticsearch:
        uris: http://localhost:9200
        index-name: vector_store
```

更多向量库支持请参考：https://springdoc.cn/spring-ai/api/vectordbs.html

## 🔍 自定义混合检索策略

### 默认混合检索

系统提供基于 Elasticsearch 的混合检索能力（向量检索 + 关键词检索）。

启用混合检索：

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        vector-store:
          enable-hybrid-search: true
          elasticsearch-min-score: 0.5
```

### 扩展其他向量库的混合检索

如需为其他向量库实现混合检索，可以继承 `AbstractHybridRetrievalStrategy`：

```java
@Component
public class CustomHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {
    
    @Override
    public List<Document> retrieve(String query, 
                                   int topK, 
                                   Filter filter,
                                   VectorStore vectorStore) {
        // 1. 向量检索
        List<Document> vectorResults = vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(topK)
                .withSimilarityThreshold(similarityThreshold)
                .withFilterExpression(filter)
        );
        
        // 2. 关键词检索（自定义实现）
        List<Document> keywordResults = performKeywordSearch(query, topK);
        
        // 3. 结果融合
        return fusionStrategy.fuse(vectorResults, keywordResults, topK);
    }
    
    private List<Document> performKeywordSearch(String query, int topK) {
        // 实现关键词检索逻辑
        // ...
    }
}
```

注册自定义策略：

```java
@Configuration
public class HybridRetrievalConfig {
    
    @Bean
    public HybridRetrievalStrategyFactory hybridRetrievalStrategyFactory(
            CustomHybridRetrievalStrategy customStrategy) {
        return new HybridRetrievalStrategyFactory() {
            @Override
            public AbstractHybridRetrievalStrategy getObject() {
                return customStrategy;
            }
        };
    }
}
```

## 🐍 Python 执行环境配置

### 执行器类型

系统支持三种Python执行器：

1. **Docker Executor** (推荐)
2. **Local Executor**
3. **AI Simulation Executor**

### Docker 执行器配置

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        code-executor:
          type: docker
          docker:
            image: continuumio/anaconda3:latest
            timeout: 300000  # 5分钟超时
            memory-limit: 512m
            cpu-limit: 1.0
```

### Local 执行器配置

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        code-executor:
          type: local
          local:
            python-path: /usr/bin/python3
            timeout: 300000
            work-dir: /tmp/dataagent
```

### AI 模拟执行器

用于测试环境，不实际执行Python代码，而是通过AI模拟执行结果：

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        code-executor:
          type: ai-simulation
```

## ⚙️ 高级配置选项

### LLM 服务类型

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        llm-service-type: STREAM  # STREAM 或 BLOCK
```

- `STREAM`: 流式输出，适合实时交互
- `BLOCK`: 阻塞式输出，等待完整结果

### 多轮对话配置

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        multi-turn:
          enabled: true
          max-history: 10  # 最大历史轮数
          context-window: 4096  # 上下文窗口大小
```

### 计划执行配置

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        plan-executor:
          max-retry: 3  # 最大重试次数
          timeout: 600000  # 10分钟超时
```

## 📊 性能优化建议

### 1. 向量检索优化

- 使用持久化向量库替代内存向量库
- 调整相似度阈值以平衡召回率和准确率
- 启用混合检索提高召回质量

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        vector-store:
          similarity-threshold: 0.3  # 根据实际情况调整
          topk-limit: 20  # 减少返回文档数量
          enable-hybrid-search: true
```

### 2. 模型调用优化

- 使用流式输出提升用户体验
- 合理设置超时时间
- 启用模型缓存（如适用）

### 3. Python 执行优化

- 使用 Docker 执行器隔离环境
- 设置合理的资源限制
- 实现执行结果缓存

### 4. 数据库优化

- 为常用查询字段添加索引
- 使用连接池管理数据库连接
- 定期清理历史数据

## 🔧 故障排查

### MCP 服务器连接失败

1. 检查端口是否被占用
2. 验证防火墙设置
3. 确认 SSE 端点配置正确

### 向量检索无结果

1. 检查向量库是否正确初始化
2. 验证 embedding 维度是否匹配
3. 调整相似度阈值

### Python 执行失败

1. 检查 Docker 服务是否运行
2. 验证镜像是否正确拉取
3. 查看执行日志定位错误

### API Key 验证失败

1. 确认 API Key 已启用
2. 检查请求头格式是否正确
3. 验证后端鉴权逻辑是否实现

## 📚 相关文档

- [快速开始](QUICK_START.md) - 基础配置和安装
- [架构设计](ARCHITECTURE.md) - 系统架构和技术实现
- [开发者文档](DEVELOPER_GUIDE.md) - 贡献指南
