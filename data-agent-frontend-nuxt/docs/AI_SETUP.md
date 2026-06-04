# AI Setup & Documentation Integration

为了确保团队成员在使用 Cursor AI 时能够获得最准确的 Nuxt 4 开发建议，请按照以下步骤配置。

## 1. 核心文档源 (Local Context)

项目已在 `docs/` 目录下集成了 Nuxt 4 的核心文档索引。Cursor 会自动索引这些本地文件。

- **本地索引**: `docs/nuxt-llms.md` (包含 Nuxt 4 官方推荐的 AI 文档索引)

## 2. 手动添加远程文档 (Cursor Docs)

虽然本地有索引，但为了让 Cursor 能够实时抓取最新的 API 详情，建议每位成员在 Cursor 中手动添加以下文档源：

### 操作步骤：
1. 打开 Cursor 侧边栏的 **Docs** 部分。
2. 点击 **+ Add New Doc**。
3. 依次添加以下两个链接：

| 名称 | 链接 | 描述 |
| :--- | :--- | :--- |
| **Nuxt4-Guide** | `https://nuxt.com/llms.txt` | Nuxt 4 核心指南与 API 概览 |
| **Nuxt4-Full** | `https://nuxt.com/llms-full.txt` | Nuxt 4 完整文档与博客内容 |

## 3. 为什么这样做？
- **减少 API 消耗**: 本地 `docs/` 下的文件作为基础上下文，减少了每次询问都去远程抓取的频率。
- **提高准确性**: `llms.txt` 是 Nuxt 官方专门为 AI 优化的格式，比普通网页抓取更精准。
- **团队同步**: 通过 Git 维护此文件，确保新成员入职时能快速完成 AI 环境配置。
