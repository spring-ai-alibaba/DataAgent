[中文](./ADVANCED_FEATURES.md) | English

# Advanced Features

This document introduces the advanced features and custom configuration options of DataAgent.

## Access API (API Key Calls)

> **Note**: The current version only provides API Key generation, reset, deletion, and enable/disable management capabilities. **Backend validation for `X-API-Key` has not been implemented yet**; for production scenarios requiring authentication, please add validation logic in the backend interceptor before exposing externally.

### API Key Management

1. Enter "Access API" from the left menu in the agent details
2. Generate a Key for the agent and enable/disable as needed
3. Add `X-API-Key: <your_api_key>` to the request header when calling session interfaces

![Access API Key](../img/apikey.png)

### API Call Examples

#### Create Session

```bash
curl -X POST "http://127.0.0.1:3000/api/agent/<agentId>/sessions" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your_api_key>" \
  -d '{"title":"demo"}'
```

#### Send Message

```bash
curl -X POST "http://127.0.0.1:3000/api/sessions/<sessionId>/messages" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your_api_key>" \
  -d '{"role":"user","content":"Give me an example","messageType":"text"}'
```

### Implement Custom Authentication

If you need to enable API Key authentication in production, you can create an interceptor:

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

        // Validate API Key
        boolean isValid = agentService.validateApiKey(apiKey);

        if (!isValid) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        return true;
    }
}
```

## MCP Server

DataAgent supports serving as an MCP (Model Context Protocol) server to provide external services.

### Configuration Description

This project implements MCP server functionality through **Mcp Server Boot Starter**.

For more detailed configuration, please refer to the official documentation:
https://springdoc.cn/spring-ai/api/mcp/mcp-server-boot-starter-docs.html#_configuration_properties

### Endpoint Configuration

**Default Configuration**:
- Custom SSE endpoint path for MCP Web transport: `project_address:project_port/sse`
- Example: `http://localhost:8065/sse`

**Custom Endpoint**:

You can modify the endpoint path through configuration:

```yaml
spring:
  ai:
    mcp:
      server:
        sse-endpoint: /custom-mcp-endpoint
```

### Available Tools

#### 1. nl2SqlToolCallback

Converts natural language queries to SQL statements.

```json
{
  "name": "nl2SqlToolCallback",
  "description": "Converts natural language queries to SQL statements. Uses the specified agent to convert user's natural language query descriptions into executable SQL statements, supporting complex data query requirements.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "nl2SqlRequest": {
        "type": "object",
        "properties": {
          "agentId": {
            "type": "string",
            "description": "Agent ID, specifies which agent to use for NL2SQL conversion"
          },
          "naturalQuery": {
            "type": "string",
            "description": "Natural language query description, e.g.: 'Query the top 10 products with highest sales'"
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

**Usage Example**:

```json
{
  "nl2SqlRequest": {
    "agentId": "agent-123",
    "naturalQuery": "Query the top 10 products with highest sales in the past 30 days"
  }
}
```

#### 2. listAgentsToolCallback

Queries the agent list, supports filtering by status and keywords.

```json
{
  "name": "listAgentsToolCallback",
  "description": "Queries the agent list, supports filtering by status and keywords. Can filter by agent status (such as PUBLISHED, DRAFT, etc.) or search agent names, descriptions, or tags by keywords. Returns agent list sorted by creation time in descending order.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "agentListRequest": {
        "type": "object",
        "properties": {
          "keyword": {
            "type": "string",
            "description": "Search agent name or description by keyword"
          },
          "status": {
            "type": "string",
            "description": "Filter by status, e.g., 'Status: draft-pending publish, published-published, offline-offline'"
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

**Usage Example**:

```json
{
  "agentListRequest": {
    "keyword": "sales",
    "status": "published"
  }
}
```

### Local Debugging

Use MCP Inspector for local debugging:

```bash
npx @modelcontextprotocol/inspector http://localhost:8065/mcp/connection
```

This will open a debugging interface where you can test various features of the MCP server.

## Logical Foreign Key Support

### Feature Overview

In actual production environments, many databases don't set physical foreign key constraints for performance considerations, which leads to the following issues:
- LLM cannot automatically infer table relationships
- Multi-table JOIN query accuracy decreases
- Complex business query failure rate increases

DataAgent innovatively implements **logical foreign key configuration functionality**, allowing users to manually define table relationships, significantly improving the accuracy of multi-table queries.

### Business Scenarios

Typical scenarios include:
- Order table and user table are related through `user_id`, but no foreign key is set in the database
- The relationship between product table and category table is not reflected at the database level
- Table relationships in legacy systems only exist in business logic

### Data Model

Logical foreign key information is stored in the `logical_relation` table:

```sql
CREATE TABLE logical_relation (
  id INT PRIMARY KEY AUTO_INCREMENT,
  datasource_id INT NOT NULL,           -- Associated data source
  source_table_name VARCHAR(100),       -- Source table name
  source_column_name VARCHAR(100),      -- Source table field
  target_table_name VARCHAR(100),       -- Target table name
  target_column_name VARCHAR(100),      -- Target table field
  relation_type VARCHAR(20),            -- Relationship type: 1:1, 1:N, N:1
  description VARCHAR(500),             -- Business description
  FOREIGN KEY (datasource_id) REFERENCES datasource(id)
);
```

### Workflow

The processing flow for logical foreign keys is as follows:

```
Frontend adds logical foreign key
    ↓
Load logical foreign keys during Schema recall
    ↓
Filter foreign keys related to recalled tables
    ↓
Merge physical and logical foreign keys
    ↓
Generate SQL based on complete Schema
```

### Technical Implementation

#### 1. Get Logical Foreign Keys

The system automatically retrieves related logical foreign keys during the Schema recall phase:

```java
private List<String> getLogicalForeignKeys(Integer agentId,
        List<Document> tableDocuments) {

    // 1. Get current agent's data source
    AgentDatasource agentDatasource =
        agentDatasourceService.getCurrentAgentDatasource(agentId);

    // 2. Extract recalled table name list
    Set<String> recalledTableNames = tableDocuments.stream()
        .map(doc -> (String) doc.getMetadata().get("name"))
        .collect(Collectors.toSet());

    // 3. Query all logical foreign keys for this data source
    List<LogicalRelation> allLogicalRelations =
        datasourceService.getLogicalRelations(datasourceId);

    // 4. Filter to keep only foreign keys related to recalled tables
    List<String> formattedForeignKeys = allLogicalRelations.stream()
        .filter(lr -> recalledTableNames.contains(lr.getSourceTableName())
                   || recalledTableNames.contains(lr.getTargetTableName()))
        .map(lr -> String.format("%s.%s=%s.%s",
            lr.getSourceTableName(), lr.getSourceColumnName(),
            lr.getTargetTableName(), lr.getTargetColumnName()))
        .distinct()
        .collect(Collectors.toList());

    return formattedForeignKeys;
}
```

**Key Features**:
- Only retrieves logical foreign keys related to recalled tables, avoiding unnecessary information interference
- Formats into unified foreign key description format: `table1.column1=table2.column2`
- Auto-deduplicates to avoid duplicate definitions

#### 2. Aggregate Foreign Key Information

In the `TableRelationNode` node, logical foreign keys are merged into physical foreign keys:

```java
private SchemaDTO buildInitialSchema(String agentId,
        List<Document> columnDocuments,
        List<Document> tableDocuments,
        DbConfig agentDbConfig,
        List<String> logicalForeignKeys) {

    SchemaDTO schemaDTO = new SchemaDTO();

    // Build basic Schema (including physical foreign keys)
    schemaService.buildSchemaFromDocuments(agentId,
        columnDocuments, tableDocuments, schemaDTO);

    // Merge logical foreign keys into Schema's foreignKeys field
    if (logicalForeignKeys != null && !logicalForeignKeys.isEmpty()) {
        List<String> existingForeignKeys = schemaDTO.getForeignKeys();
        if (existingForeignKeys == null || existingForeignKeys.isEmpty()) {
            // When no physical foreign keys, use logical foreign keys directly
            schemaDTO.setForeignKeys(logicalForeignKeys);
        } else {
            // Merge physical and logical foreign keys
            List<String> allForeignKeys = new ArrayList<>(existingForeignKeys);
            allForeignKeys.addAll(logicalForeignKeys);
            schemaDTO.setForeignKeys(allForeignKeys);
        }
        log.info("Merged {} logical foreign keys into schema",
            logicalForeignKeys.size());
    }

    return schemaDTO;
}
```

**Design Advantages**:
- Physical and logical foreign keys are processed uniformly, transparent to downstream
- Logical foreign keys have the same priority as physical foreign keys
- Complete foreign key information improves LLM's understanding of table relationships

### Usage Example

#### Configure Logical Foreign Keys

In the frontend data source management interface:

1. Select data source
2. Enter "Logical Foreign Key Management"
3. Add foreign key relationship:
   - Source Table: `orders`
   - Source Field: `user_id`
   - Target Table: `users`
   - Target Field: `id`
   - Relationship Type: `N:1`
   - Description: "Order table associated with user table"

#### Effect Comparison

**Without Logical Foreign Key Configuration**:
```
User Question: "Query all orders for user Zhang San"
Generated SQL: SELECT * FROM orders WHERE user_name = 'Zhang San'  -- Wrong
```

**With Logical Foreign Key Configuration**:
```
User Question: "Query all orders for user Zhang San"
Generated SQL:  -- Correct
SELECT o.*
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE u.name = 'Zhang San'
```

### Best Practices

1. **Prioritize High-Frequency Associations**: Configure the most frequently used table associations in business first
2. **Add Description Information**: Detailed relationship descriptions help LLM understand business semantics
3. **Regular Maintenance**: Update logical foreign key configurations in a timely manner as business changes
4. **Accurate Relationship Types**: Correctly mark 1:1, 1:N, N:1 relationships to improve inference accuracy

### Notes

- Logical foreign key configuration is only used for Schema enhancement, it won't affect actual database structure
- Incorrect logical foreign key configuration may lead to incorrect SQL generation
- It's recommended to confirm table relationship accuracy with database administrators

## Python Execution Environment Configuration

### Executor Types

The system supports three Python executors:

1. **Docker Executor** (Recommended)
2. **Local Executor**
3. **AI Simulation Executor**

### Docker Executor Configuration

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        code-executor:
          type: docker
          docker:
            image: continuumio/anaconda3:latest
            timeout: 300000  # 5 minute timeout
            memory-limit: 512m
            cpu-limit: 1.0
```

### Local Executor Configuration

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

### AI Simulation Executor

Used for testing environments, doesn't actually execute Python code, but simulates execution results through AI:

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        code-executor:
          type: ai-simulation
```

## Advanced Configuration Options

### LLM Service Type

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        llm-service-type: STREAM  # STREAM or BLOCK
```

- `STREAM`: Streaming output, suitable for real-time interaction
- `BLOCK`: Blocking output, waits for complete result

### Multi-turn Conversation Configuration

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        multi-turn:
          enabled: true
          max-history: 10  # Maximum history turns
          context-window: 4096  # Context window size
```

### Plan Executor Configuration

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        plan-executor:
          max-retry: 3  # Maximum retry count
          timeout: 600000  # 10 minute timeout
```

## Related Documents

- [Quick Start](QUICK_START-en.md) - Basic configuration and installation
- [Architecture Design](ARCHITECTURE-en.md) - System architecture and technical implementation
- [Developer Documentation](DEVELOPER_GUIDE-en.md) - Contribution guide
