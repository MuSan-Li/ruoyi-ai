# ruoyi-ai-codereview 模块使用指南

## 一、模块概述

ruoyi-ai-codereview 是一个基于 AI 的代码审查模块，支持 GitLab、GitHub、Gitea 等平台的自动化代码审查。

## 二、功能特性

### 2.1 多平台支持
- GitLab (MR 事件)
- GitHub (PR 事件)
- Gitea (PR 事件)

### 2.2 审查类型
- MR/PR 审查：审查合并请求中的代码变更
- Push 审查：审查推送的代码变更

### 2.3 通知渠道
- 钉钉 (DingTalk)
- 飞书 (Feishu)
- 企业微信 (WeCom)
- 自定义 Webhook

## 三、快速开始

### 3.1 执行菜单 SQL

在数据库中执行以下 SQL 文件：

```bash
ruoyi-ai-codereview/src/main/resources/db/codereview_menu.sql
```

这会创建以下菜单：
- 代码审查（一级目录）
  - 项目配置（管理项目配置）
  - 审查日志（查看审查记录）
  - 统计分析（查看统计数据）

### 3.2 配置项目

1. 登录管理后台
2. 进入「代码审查」→「项目配置」
3. 点击「新增」按钮创建项目配置

**配置项说明：**

| 配置项 | 说明 |
|--------|------|
| 项目名称 | 与 Git 平台上的项目名称一致 |
| 平台 | 选择 GitLab/GitHub/Gitea |
| 平台地址 | Git 服务器地址，如 https://gitlab.example.com |
| 平台 Token | 访问令牌，需要有读取项目和发布评论的权限 |
| Webhook 密钥 | 用于验证 Webhook 请求的密钥 |
| AI 模型 | 使用的 AI 模型 ID（从模型管理中选择） |

### 3.3 配置 Webhook

在 Git 平台上配置 Webhook：

**GitLab:**
- URL: `http://your-server/codereview/webhook/gitlab`
- Secret Token: 填写项目配置中的 Webhook 密钥
- Triggers: 选择 Merge request events, Push events

**GitHub:**
- URL: `http://your-server/codereview/webhook/github`
- Secret: 填写项目配置中的 Webhook 密钥
- Events: 选择 Pull request, Push

**Gitea:**
- URL: `http://your-server/codereview/webhook/gitea`
- Secret: 填写项目配置中的 Webhook 密钥
- Events: 选择 Pull Request, Push

### 3.4 配置通知渠道

在项目配置的「通知配置」标签页中：

1. 点击「+ 添加通知渠道」
2. 选择通知类型（钉钉/飞书/企业微信/Webhook）
3. 填写 Webhook URL
4. 钉钉需要额外配置签名密钥
5. 启用渠道

### 3.5 自定义审查规则

在项目配置的「自定义规则」标签页中：

1. 点击「+ 添加审查规则」
2. 填写规则名称和描述
3. AI 在审查时会参考这些规则

## 四、审查配置详解

### 4.1 审查开关

| 配置项 | 说明 |
|--------|------|
| 启用 MR 审查 | 是否审查 MR/PR 事件 |
| 启用 Push 审查 | 是否审查 Push 事件 |
| 仅审查保护分支 | 只审查保护分支的 Push 事件 |

### 4.2 审查参数

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 审查风格 | professional | 代码审查风格 |
| 通过分数 | 60 | 审查通过的分值线 |
| 最大 Token 数 | 10000 | AI 处理的最大 Token 数 |
| 最大文件数 | 20 | 单次审查的最大文件数 |
| 文件扩展名 | .java,.js,.ts,.vue,.py,.go | 需要审查的文件类型 |
| 排除模式 | - | 不审查的文件/目录模式 |

### 4.3 审查风格

- **professional**: 专业详细，包含代码质量、安全性、性能等多维度分析
- **concise**: 简洁精炼，只关注关键问题
- **educational**: 教学风格，适合新人团队

## 五、API 接口

### 5.1 项目配置管理

```http
# 获取项目配置列表
GET /codereview/projectConfig/list

# 获取项目配置详情
GET /codereview/projectConfig/{id}

# 新增项目配置
POST /codereview/projectConfig

# 修改项目配置
PUT /codereview/projectConfig

# 删除项目配置
DELETE /codereview/projectConfig/{ids}

# 测试平台连接
POST /codereview/projectConfig/testConnection

# 测试通知发送
POST /codereview/projectConfig/testNotification

# 切换审查状态
PUT /codereview/projectConfig/changeStatus
```

### 5.2 审查日志

```http
# MR 审查日志列表
GET /codereview/reviewLog/mr/list

# MR 审查日志详情
GET /codereview/reviewLog/mr/{id}

# Push 审查日志列表
GET /codereview/reviewLog/push/list

# Push 审查日志详情
GET /codereview/reviewLog/push/{id}
```

### 5.3 统计分析

```http
# 获取趋势数据
GET /codereview/statistics/trend?projectName=xxx&platform=xxx&days=7

# 生成趋势报告
GET /codereview/statistics/report?projectName=xxx&platform=xxx&days=7
```

## 六、权限配置

菜单 SQL 中定义了以下权限：

| 权限标识 | 说明 |
|----------|------|
| codereview:projectConfig:list | 项目配置列表 |
| codereview:projectConfig:query | 项目配置查询 |
| codereview:projectConfig:add | 项目配置新增 |
| codereview:projectConfig:edit | 项目配置修改 |
| codereview:projectConfig:remove | 项目配置删除 |
| codereview:reviewLog:list | 审查日志列表 |
| codereview:reviewLog:query | 审查日志查询 |
| codereview:reviewLog:remove | 审查日志删除 |
| codereview:statistics:query | 统计分析查询 |
| codereview:statistics:edit | 统计分析刷新 |

## 七、多项目配置示例

### 场景：多项目独立通知

假设有以下项目：
1. `backend-service` - 后端服务项目，通知到钉钉开发群
2. `frontend-app` - 前端项目，通知到飞书前端群

**配置步骤：**

1. 创建 `backend-service` 项目配置：
   - 项目名称：backend-service
   - 平台：GitLab
   - 通知渠道：钉钉，Webhook URL 为钉钉群机器人地址

2. 创建 `frontend-app` 项目配置：
   - 项目名称：frontend-app
   - 平台：GitLab
   - 通知渠道：飞书，Webhook URL 为飞书群机器人地址

当各自的 Webhook 触发时，系统会根据项目名称找到对应配置，并发送通知到指定渠道。

## 八、监控指标

模块集成了 Micrometer 监控指标：

- `codereview.reviews.total`: 审查总数计数器
- `codereview.reviews.duration`: 审查耗时计时器
- `codereview.reviews.score`: 评分分布统计
- `codereview.llm.calls`: LLM 调用次数
- `codereview.llm.tokens`: Token 使用量
- `codereview.webhook.requests`: Webhook 请求次数

可通过 `/actuator/metrics` 端点查看。

## 九、健康检查

访问 `/actuator/health` 可查看模块健康状态：

```json
{
  "status": "UP",
  "components": {
    "codeReview": {
      "status": "UP",
      "details": {
        "totalProjects": 5,
        "enabledProjects": 3,
        "cacheStatus": "healthy"
      }
    }
  }
}
```

## 十、常见问题

### Q1: Webhook 验证失败
检查项目配置中的 Webhook 密钥是否与 Git 平台配置一致。

### Q2: AI 审查无响应
1. 检查 AI 模型配置是否正确
2. 检查模型的 API Key 是否有效
3. 查看日志中的错误信息

### Q3: 通知发送失败
1. 检查 Webhook URL 是否正确
2. 钉钉需检查签名密钥配置
3. 使用「测试通知」功能验证配置

### Q4: 某些文件未被审查
检查「文件扩展名」和「排除模式」配置是否符合预期。
