# 通知功能设计

## 1. 概述

为 DataAgent 添加通知功能，在工作流结束时向用户发送钉钉消息通知。采用工厂模式，支持用户扩展其他通知渠道（飞书、企业微信等）。

## 2. 触发时机

- **ReportGeneratorNode 完成时** - 分析任务正常结束
- **HumanFeedbackNode 确认后** - 用户人工干预确认

## 3. 核心组件

### 3.1 NotificationInfo

通知信息对象，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| nodeName | String | 触发节点名称 |
| status | String | 任务状态（成功/失败） |
| timestamp | LocalDateTime | 触发时间 |
| threadId | String | 线程ID（可选） |
| agentId | String | 代理ID（可选） |

### 3.2 NotifierService

通知接口：

```java
public interface NotifierService {
    void notify(NotificationInfo info);
    boolean supports(String channel);
}
```

### 3.3 NotifierFactory

工厂类：
- 根据配置 `spring.ai.alibaba.data-agent.notify.channel` 创建对应通知实现
- 使用 Spring SPI 机制加载用户自定义实现
- 默认实现：DingTalkNotifier

### 3.4 DingTalkNotifier

钉钉通知实现：
- **安全验证**：HmacSHA256 签名
- **消息格式**：Markdown
- **配置项**：
  - `webhook-url`：钉钉机器人 Webhook 地址
  - `secret-key`：签名密钥

## 4. 配置项

```yaml
spring:
  ai:
    alibaba:
      data-agent:
        notify:
          enabled: true
          channel: dingtalk
          dingtalk:
            webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
            secret-key: xxxxx
```

## 5. 消息格式

```markdown
## DataAgent 任务通知

- **状态**: 成功 ✓
- **触发节点**: ReportGeneratorNode
- **时间**: 2026-04-21 15:30:25
```

## 6. 数据流

```
GraphServiceImpl
    ├── ReportGeneratorNode.apply() 完成
    │       → NotifierFactory.create().notify(info)
    │
    └── HumanFeedbackNode 处理完成
            → NotifierFactory.create().notify(info)
```

## 7. 文件清单

| 文件 | 说明 |
|------|------|
| `NotificationInfo.java` | 通知信息对象 |
| `NotifierService.java` | 通知服务接口 |
| `NotifierFactory.java` | 通知工厂类 |
| `DingTalkNotifier.java` | 钉钉通知实现 |
| `application.yml` | 配置项 |
| `GraphServiceImpl.java` | 触发通知调用 |

## 8. 扩展方式

用户实现 `NotifierService` 接口并注册为 Spring Bean，工厂会自动加载：

```java
@Component
public class CustomNotifier implements NotifierService {
    @Override
    public void notify(NotificationInfo info) {
        // 自定义通知逻辑
    }
    @Override
    public boolean supports(String channel) {
        return "custom".equals(channel);
    }
}
```
