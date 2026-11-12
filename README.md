# 政策分析系统（Policy Intelligence Platform）

AI 驱动的政策信息采集、分析与问答平台。系统提供政策知识库、后台管理、NLP 辅助分析及“政策分析助手”浮动聊天球，帮助业务人员快速定位权威政策来源并生成决策参考。

## 🔔 目录
- [产品概述](#产品概述)
- [关键特性](#关键特性)
- [系统架构](#系统架构)
- [快速上手](#快速上手)
- [运行与测试](#运行与测试)
- [政策分析助手工作流](#政策分析助手工作流)
- [配置说明](#配置说明)
- [目录结构](#目录结构)
- [常见问题](#常见问题)
- [规划与贡献](#规划与贡献)
- [许可证](#许可证)

---

## 产品概述
政策分析系统集成了政策数据采集、结构化存储、可视化呈现及大模型问答能力。用户可以在统一平台内完成政策检索、趋势分析、全文预览与导出，配合 AI 助手实现自然语言问答与结构化洞察。

系统定位：
- 政策研究团队的日常检索与情报分析工具
- 企业合规/战略部门的政策跟踪、研判助手
- 地方政府或智库的政策知识库与交互式问答平台

## 关键特性
### 1. 政策分析助手
- 页面内的玻璃态悬浮球，可拖拽、缩放。
- 支持实时流式（SSE）输出，带“打字机”效果。
- 通过提示词约束确保实名引用来源、禁止模型幻觉。
- 自动生成 SQL 查询 MySQL，执行后再综合回答，保证结果可靠可追溯。

### 2. 政策知识库
- 支持政策列表、机构统计、趋势图等多种视图。
- 提供政策原文 PDF 预览与下载，支持多来源 Referer 代理。
- 后台管理可补录/编辑政策数据，支持批量导入。

### 3. 安全合规
- Spring Security + JWT 登录鉴权，细粒度接口权限。
- SQL 执行层只允许 SELECT、限定表、自动补 LIMIT，并限制执行时间。
- 敏感信息在配置中支持环境变量覆盖，避免硬编码密钥。

### 4. 模块化与可扩展
- 架构清晰易维护：数据采集、服务逻辑、前端交互、AI 提示分层管理。
- 预留 LLM 接入接口，可替换为其他对话模型（如阿里通义、OpenAI、Grok）。
- 前端使用原生 JS + CSS，便于按需扩展 UI 组件。

## 系统架构
```
┌─────────────┐       ┌────────────┐
│ Frontend UI │──────▶│ Assistant  │───┐
│ (Thymeleaf  │       │ Controller │   │
│ + JS/CSS)   │◀──────┤ (REST/SSE) │   │
└─────────────┘       └────┬───────┘   │
                   │           │
                   ▼           │
              ┌────────────┐      │
              │PolicyAssistant │   │
              │Service      │─────┘
              ├────────────┤
              │ SqlExecution│──▶ MySQL (policy_data, policy_og)
              ├────────────┤
              │ QwenClient │──▶ LLM API (Qwen/Grok...)
              └────────────┘
```
- **前端**：Thymeleaf 渲染页面，配合原生 JS 实现悬浮助手、表格渲染、图表展示。
- **后端**：Spring Boot 3.2.5，分层包括 Controller / Service / Repository / LLM 客户端。
- **数据库**：MySQL 8.x，核心表 `policy_data`（政策内容）与 `policy_og`（发布机构）。
- **模型服务**：`QwenClient` 通过 HTTP 调用通义千问 API，并按需配置 Grok 等。支持 stream=false 直出文本。
- **消息流**：SSE 负责将模型生成的回复流式推送给前端，前端做打字机动画与滚动。

## 快速上手
### 1. 环境准备
- Java 17
- Maven 3.9+
- MySQL 8.x
- 可访问外部 LLM 接口的网络（若在内网需配置代理）

### 2. 克隆项目
```powershell
git clone https://github.com/BeiQI1/policy.git
cd policy
```

### 3. 初始化数据库
```sql
CREATE DATABASE policy DEFAULT CHARACTER SET utf8mb4;
``` 
执行初始化脚本：
```powershell
mysql -uroot -p policy < schema.sql
mysql -uroot -p policy < sample-data.sql        # 可选，包含示例数据
```

### 4. 配置环境变量（推荐）
```powershell
setx SPRING_DATASOURCE_URL "jdbc:mysql://localhost:3306/policy?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
setx QWEN_API_KEY "sk-你的通义密钥"
# 如使用 Grok： setx GROK_API_KEY "sk-xxxx"
```

### 5. 启动应用
```powershell
mvn spring-boot:run
```
访问 `http://localhost:8989`（如端口在配置中修改，请以实际为准）。

### 6. 默认账号
- 管理员：`admin / admin123`
- 登录后可访问后台页面、政策助手、统计视图。

## 运行与测试
- **打包构建**：
  ```powershell
  mvn -DskipTests package
  ```
  产物位于 `target/policy-0.0.1-SNAPSHOT.jar`，可通过 `java -jar` 部署。

- **运行测试**：
  ```powershell
  mvn test
  ```
  若需自定义数据源，请在执行前调整 `application.yml` 或设置 `SPRING_DATASOURCE_*`。

- **生产部署建议**：
  - 使用 `systemd` 或容器编排守护进程。
  - 将 `application.yml` 中敏感字段（API Key、数据库密码）替换为环境变量。
  - 配合 Nginx 等前置反向代理实现域名、HTTPS、限流。

## 政策分析助手工作流
1. **输入采集**：用户在悬浮球中输入自然语言问题。
2. **历史截断**：前端保留最近 10 条对话历史，提交到后端。
3. **策略规划**：
  - `PolicyAssistantService` 调用 `QwenClient.complete()` 请求模型生成动作计划。
  - 若模型返回 `sql.query` 动作→进入第 4 步，若返回 `final.answer`→直接返回。
4. **SQL 校验执行**：
  - `SqlExecutionService` 检查语句仅包含 SELECT、无多语句/注释/写操作、仅访问白名单表，并自动补 LIMIT（默认 50）。
  - 执行结果格式化为 Markdown 表格，警告信息附加在 `thoughts` 列表。
5. **总结回答**：模型拿到查询结果后生成最终回答，包括政策要点、时间、来源链接。
6. **流式输出**：后端通过 SSE 将 meta（来源/思考）、delta（逐段内容）和 done（收尾）推送到前端，实现打字机体验。

前端文件 `static/js/assistant-widget.js` 实现以下功能：
- 面板拖拽、缩放与位置记忆（LocalStorage）。
- 对 SSE 事件解析：`meta`（填充来源表格）、`delta`（追加文本）、`done`（收尾）、`error`（异常提示）。
- 发送请求时附带 JWT 令牌，若未登录显示提示并关闭面板。

## 配置说明
| 分类 | Key | 默认值 | 说明 |
| --- | --- | --- | --- |
| 服务 | `server.port` | 8080 | Spring Boot 端口，可自定义 |
| 数据库 | `spring.datasource.*` |见 `application.yml`| MySQL 连接信息 |
| JPA | `spring.jpa.*` | `ddl-auto=none` | 禁用自动建表，生产需配合脚本 |
| 助手模型 | `llm.qwen.api-key` | `${QWEN_API_KEY}` | 从环境读取通义 API Key |
| 模型 | `llm.qwen.model` | `qwen3-235b-a22b` | 可替换为合适规格 |
| SQL | `assistant.sql.max-limit` | 50 | 查询最大返回条数 |
| SQL | `assistant.sql.timeout-seconds` | 12 | 超时时间（秒） |
| 安全 | `jwt.secret` | 默认示例值 | 生产务必替换为随机长密钥 |

更多提示词、采集策略在 `src/main/resources/prompts/`、`src/main/java/com/example/policy/crawler/` 等目录中维护。

## 目录结构
```
policy/
├─ schema.sql / sample-data.sql      # 数据库结构与样例数据
├─ src/main/java/com/example/policy
│   ├─ assistant/                    # LLM 助手逻辑
│   │   ├─ client/QwenClient.java
│   │   ├─ service/PolicyAssistantService.java
│   │   └─ service/SqlExecutionService.java
│   ├─ controller/                   # 页面与接口控制器
│   │   ├─ PolicyController.java
│   │   ├─ PdfController.java        # PDF 代理预览
│   │   └─ AssistantController.java  # /api/assistant/chat
│   ├─ security/                     # Spring Security & JWT
│   ├─ service/                      # 业务服务 (PolicyDataService 等)
│   └─ repository/                   # JPA Repository
├─ src/main/resources
│   ├─ templates/                    # Thymeleaf 模板
│   ├─ static/css & static/js        # 前端资源（悬浮球、图表等）
│   ├─ prompts/                      # LLM 提示词模板
│   └─ application.yml               # 默认配置
└─ README.seed.md                    # 精简占位稿，可忽略
```

## 常见问题
1. **模型提示报错或 `HTTP 401`？**
  - 确认环境变量 `QWEN_API_KEY`、`GROK_API_KEY` 是否配置。
  - 查看网络是否可访问模型 API 所在域名。

2. **SSE 提示网络错误？**
  - 确保前端已登录（JWT 可用），否则 `/api/assistant/chat` 会被拦截。
  - 检查后端日志中是否存在 SQL 校验失败或模型异常。

3. **SQL 查询过慢或结果为空？**
  - 调整 `assistant.sql.timeout-seconds` 或 `max-limit`。
  - 查看 `policy_data` 中是否已有数据；必要时使用后台导入。

4. **PDF 无法预览？**
  - 检查政策条目是否包含 `pdfUrl`。
  - 某些来源会要求特定 Referer/UA，可在 `PdfController` 中按需调整。

## 规划与贡献
> 该项目仍在积极开发中，欢迎提出 Issue/PR。

潜在 Roadmap：
- [ ] 引入 Flyway 管理数据库变更
- [ ] 扩展 NLP 标注与关键词抽取能力
- [ ] 支持多模型自动切换与回退策略
- [ ] 增强仪表盘可视化（ECharts、地图分布）
- [ ] 提供 Docker Compose 一键部署方案

贡献指南：
1. Fork 项目并创建分支：`git checkout -b feature/xxx`
2. 完成功能后跑通 `mvn test`
3. 提交描述清晰的 PR，并注明影响范围、测试情况。

## 许可证
本仓库默认遵循 MIT License，如需商业使用或二次开发，请保留版权声明并遵循相关条款。

---
如需更多帮助，可在 Issue 中提问或联系维护者。祝使用愉快！
