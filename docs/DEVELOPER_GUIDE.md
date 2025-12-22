# 开发者文档

欢迎参与 DataAgent 项目的开发！本文档将帮助您了解如何为项目做出贡献。

## 🚀 开发环境搭建

### 前置要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **Node.js**: 16 或更高版本
- **MySQL**: 5.7 或更高版本
- **Git**: 版本控制工具
- **IDE**: IntelliJ IDEA 或 Eclipse (推荐 IntelliJ IDEA)

### 克隆项目

```bash
git clone https://github.com/your-org/spring-ai-alibaba-data-agent.git
cd spring-ai-alibaba-data-agent
```

### 后端开发环境

1. **导入项目到 IDE**
   - 使用 IntelliJ IDEA 打开项目根目录
   - IDE 会自动识别为 Maven 项目并下载依赖

2. **配置数据库**
   - 创建 MySQL 数据库
   - 修改 `data-agent-management/src/main/resources/application.yml` 中的数据库配置

3. **启动后端服务**
   ```bash
   cd data-agent-management
   ./mvnw spring-boot:run
   ```

### 前端开发环境

1. **安装依赖**
   ```bash
   cd data-agent-frontend
   npm install
   ```

2. **启动开发服务器**
   ```bash
   npm run dev
   ```

3. **访问应用**
   - 打开浏览器访问 http://localhost:3000

## 📁 项目结构详解

### 后端结构

```
data-agent-management/
├── src/main/java/com/alibaba/cloud/ai/dataagent/
│   ├── controller/              # REST API 控制器
│   │   ├── AgentController.java
│   │   ├── GraphController.java
│   │   ├── ModelConfigController.java
│   │   └── PromptConfigController.java
│   ├── service/                 # 业务服务层
│   │   ├── impl/
│   │   │   ├── GraphServiceImpl.java
│   │   │   ├── AgentServiceImpl.java
│   │   │   └── LlmService.java
│   │   └── hybrid/              # 混合检索策略
│   ├── workflow/                # StateGraph 工作流
│   │   ├── node/                # 工作流节点
│   │   │   ├── IntentRecognitionNode.java
│   │   │   ├── EvidenceRecallNode.java
│   │   │   ├── PlannerNode.java
│   │   │   ├── SqlGenerateNode.java
│   │   │   ├── PythonGenerateNode.java
│   │   │   └── ReportGeneratorNode.java
│   │   └── dispatcher/          # 节点调度器
│   ├── model/                   # 数据模型
│   │   ├── entity/              # 数据库实体
│   │   ├── dto/                 # 数据传输对象
│   │   └── vo/                  # 视图对象
│   ├── mapper/                  # MyBatis Mapper
│   ├── config/                  # 配置类
│   │   ├── AiModelRegistry.java
│   │   └── VectorStoreConfig.java
│   └── util/                    # 工具类
└── src/main/resources/
    ├── sql/                     # 数据库脚本
    ├── application.yml          # 应用配置
    └── mapper/                  # MyBatis XML 映射文件
```

### 前端结构

```
data-agent-frontend/
├── src/
│   ├── components/              # 可复用组件
│   │   ├── AgentCard/
│   │   ├── ChatInterface/
│   │   └── ConfigPanel/
│   ├── pages/                   # 页面组件
│   │   ├── AgentList/
│   │   ├── AgentConfig/
│   │   └── AgentRun/
│   ├── services/                # API 服务
│   │   ├── agentService.ts
│   │   ├── graphService.ts
│   │   └── modelService.ts
│   ├── hooks/                   # 自定义 Hooks
│   ├── utils/                   # 工具函数
│   └── types/                   # TypeScript 类型定义
└── public/                      # 静态资源
```

## 🔧 核心模块说明

### 1. StateGraph 工作流引擎

工作流基于 Spring AI Alibaba 的 StateGraph 实现，核心节点包括：

- **IntentRecognitionNode**: 意图识别
- **EvidenceRecallNode**: 证据召回
- **PlannerNode**: 计划生成
- **SqlGenerateNode**: SQL 生成
- **PythonGenerateNode**: Python 代码生成
- **ReportGeneratorNode**: 报告生成

### 2. 多模型调度

通过 `AiModelRegistry` 实现多模型管理和热切换：

```java
@Service
public class AiModelRegistry {
    private ChatModel currentChatModel;
    private EmbeddingModel currentEmbeddingModel;
    
    public void refreshChatModel(ModelConfig config) {
        // 动态创建和切换 Chat 模型
    }
    
    public void refreshEmbeddingModel(ModelConfig config) {
        // 动态创建和切换 Embedding 模型
    }
}
```

### 3. 向量检索服务

`AgentVectorStoreService` 提供统一的向量检索接口：

```java
@Service
public class AgentVectorStoreService {
    public List<Document> retrieve(String query, 
                                   String agentId, 
                                   VectorType vectorType) {
        // 向量检索逻辑
    }
}
```

## 🎨 编码规范

### Java 编码规范

1. **命名规范**
   - 类名：大驼峰命名法 (PascalCase)
   - 方法名：小驼峰命名法 (camelCase)
   - 常量：全大写下划线分隔 (UPPER_SNAKE_CASE)

2. **注释规范**
   - 所有公共类和方法必须有 JavaDoc 注释
   - 复杂逻辑需要添加行内注释

3. **代码格式**
   - 使用 4 个空格缩进
   - 每行代码不超过 120 字符
   - 使用 Google Java Style Guide

### TypeScript 编码规范

1. **命名规范**
   - 组件名：大驼峰命名法
   - 变量/函数：小驼峰命名法
   - 接口：I 前缀 + 大驼峰命名法

2. **类型定义**
   - 优先使用 interface 而非 type
   - 避免使用 any 类型
   - 为所有函数参数和返回值添加类型

3. **代码格式**
   - 使用 2 个空格缩进
   - 使用 Prettier 格式化代码
   - 使用 ESLint 检查代码质量

## 🧪 测试指南

### 后端测试

#### 单元测试

```java
@SpringBootTest
class GraphServiceImplTest {
    
    @Autowired
    private GraphService graphService;
    
    @Test
    void testNl2Sql() {
        // 测试 NL2SQL 功能
        String result = graphService.nl2sql("查询销售额前10的产品");
        assertNotNull(result);
    }
}
```

#### 集成测试

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class GraphControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testStreamSearch() throws Exception {
        mockMvc.perform(get("/api/graph/stream")
                .param("query", "测试查询"))
                .andExpect(status().isOk());
    }
}
```

### 前端测试

#### 组件测试

```typescript
import { render, screen } from '@testing-library/react';
import AgentCard from './AgentCard';

test('renders agent card', () => {
  const agent = { id: '1', name: 'Test Agent' };
  render(<AgentCard agent={agent} />);
  expect(screen.getByText('Test Agent')).toBeInTheDocument();
});
```

### 运行测试

```bash
# 后端测试
cd data-agent-management
./mvnw test

# 前端测试
cd data-agent-frontend
npm test
```

## 📝 提交规范

### Commit Message 格式

使用 Conventional Commits 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**:
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具链相关

**示例**:

```
feat(workflow): add python execution node

- Implement PythonExecuteNode for code execution
- Add Docker executor support
- Update workflow graph

Closes #123
```

### Pull Request 流程

1. **Fork 项目**
   ```bash
   # 在 GitHub 上 Fork 项目
   git clone https://github.com/your-username/spring-ai-alibaba-data-agent.git
   ```

2. **创建分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **开发和提交**
   ```bash
   git add .
   git commit -m "feat: add new feature"
   ```

4. **推送到远程**
   ```bash
   git push origin feature/your-feature-name
   ```

5. **创建 Pull Request**
   - 在 GitHub 上创建 PR
   - 填写 PR 描述，说明改动内容
   - 等待 Code Review

## 🔍 Code Review 指南

### Review 要点

1. **代码质量**
   - 是否符合编码规范
   - 是否有充分的注释
   - 是否有单元测试

2. **功能完整性**
   - 是否实现了预期功能
   - 是否处理了边界情况
   - 是否有错误处理

3. **性能考虑**
   - 是否有性能问题
   - 是否有内存泄漏风险
   - 是否有优化空间

4. **安全性**
   - 是否有安全漏洞
   - 是否有 SQL 注入风险
   - 是否有 XSS 风险

## 🐛 调试技巧

### 后端调试

1. **使用 IDE 断点调试**
   - 在关键代码处设置断点
   - 使用 Debug 模式启动应用
   - 逐步执行查看变量值

2. **日志调试**
   ```java
   @Slf4j
   public class YourService {
       public void yourMethod() {
           log.debug("Debug info: {}", variable);
           log.info("Info message");
           log.error("Error occurred", exception);
       }
   }
   ```

3. **使用 Actuator 监控**
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
   ```

### 前端调试

1. **使用浏览器开发者工具**
   - Console: 查看日志和错误
   - Network: 查看网络请求
   - React DevTools: 查看组件状态

2. **使用 console.log**
   ```typescript
   console.log('Debug info:', data);
   console.error('Error:', error);
   ```

## 📚 学习资源

### 官方文档

- [Spring AI Alibaba 文档](https://springdoc.cn/spring-ai/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [React 文档](https://react.dev/)
- [TypeScript 文档](https://www.typescriptlang.org/)

### 相关技术

- StateGraph 工作流引擎
- MyBatis 数据访问框架
- Vector Store 向量数据库
- Server-Sent Events (SSE)

## 🤝 贡献指南

详细的贡献指南请参考 [CONTRIBUTING-zh.md](../CONTRIBUTING-zh.md)。

### 贡献类型

- 🐛 报告 Bug
- 💡 提出新功能建议
- 📝 改进文档
- 🔧 提交代码修复
- ✨ 开发新功能

### 行为准则

- 尊重所有贡献者
- 保持友好和专业
- 接受建设性批评
- 关注项目目标

## 📞 联系方式

- **钉钉交流群**: 154405001431
- **GitHub Issues**: 提交问题和建议
- **Pull Requests**: 提交代码贡献

## 🎯 开发路线图

### 近期计划

- [ ] 支持更多向量数据库
- [ ] 优化 Python 执行性能
- [ ] 增强报告生成能力
- [ ] 完善 MCP 服务器功能

### 长期规划

- [ ] 支持多租户
- [ ] 增加更多数据源类型
- [ ] 实现分布式部署
- [ ] 提供 SaaS 服务

---

感谢您对 DataAgent 项目的贡献！🎉
