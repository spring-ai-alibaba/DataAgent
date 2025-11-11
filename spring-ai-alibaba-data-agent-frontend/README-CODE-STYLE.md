# 代码规范和检查工具配置说明

## 已配置的工具

### 1. 代码格式化工具

- **Prettier**: 自动格式化代码，确保代码风格一致
- 配置文件: `.prettierrc`
- 忽略文件: `.prettierignore`

### 2. 代码规范检查工具

- **ESLint**: 检查代码质量和规范
- **@typescript-eslint**: TypeScript 代码检查
- **eslint-plugin-vue**: Vue 组件代码检查
- 配置文件: `.eslintrc.js`
- 忽略文件: `.eslintignore`

### 3. 未使用代码检查工具

- **unimported**: 检查未使用的导入和依赖
- **knip**: 检查未使用的文件、导出和依赖
- 配置文件: `knip.json`

### 4. TypeScript 类型检查

- **vue-tsc**: Vue + TypeScript 类型检查
- 配置文件: `tsconfig.json`, `tsconfig.node.json`

## 安装步骤

1. 进入前端项目目录：

```bash
cd spring-ai-alibaba-data-agent-frontend
```

2. 安装所有依赖：

```bash
npm install
```

## 使用命令

### 代码格式化

```bash
# 格式化所有代码
npm run format

# 检查代码格式（不修改）
npm run format:check
```

### 代码规范检查

```bash
# 检查并自动修复代码规范问题
npm run lint

# 只检查不修复
npm run lint:check
```

### 未使用代码检查

```bash
# 检查未使用的导入和代码
npm run unused
```

### 组合命令

```bash
# 完整的代码质量检查
npm run lint:check && npm run format:check && npm run unused
```

## 推荐的开发流程

1. 开发时实时检查：
   - 在 VS Code 中安装 ESLint 和 Prettier 插件
   - 启用保存时自动格式化

2. 提交前检查：

   ```bash
   npm run lint && npm run format
   ```

3. 定期清理：
   ```bash
   npm run unused
   ```

## 规则说明

### Prettier 规则

- 使用单引号
- 行尾分号
- 尾随逗号
- 最大行宽 100 字符
- 缩进 2 个空格

### ESLint 规则

- 检查未使用的变量和导入
- Vue 组件命名规则
- TypeScript 严格模式
- 生产环境禁用 console 和 debugger

这些配置将帮助您的项目保持代码质量。
