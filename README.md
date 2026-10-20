# AI 智能法律咨询系统 · Legal RAG Assistant

基于 Spring Boot、Vue 3、DeepSeek、Embedding 和 Qdrant 构建的法律领域智能咨询应用，
支持法律知识库管理、RAG 法条检索、可溯源问答、案件分析、文书生成及效果评测。

> 个人项目与技术学习展示。AI 输出不能替代专业法律意见。
> 本公开版只提供应用源码、数据表结构和配置示例，不包含真实个人数据、API Key、法律知识库原始数据或向量存储。

## 项目亮点

- **RAG 双链路实现**：Basic RAG（原问题直接检索）与 Enhanced RAG（查询理解 + 查询改写）双链路可切换，附带对照评测；
- **领域化混合排序**：按“第X条”切分法条后向量化，召回阶段综合向量相似度（0.70）、关键词匹配（0.20）与法律领域匹配（0.10）加权排序，配合检索阈值判定；
- **可溯源问答**：回答附参考法条引用与原文回查，前端展示检索来源，支持从结果回溯到 MySQL 法规原文；
- **LLM 多任务编排**：DeepSeek 统一支撑咨询回答、查询理解、案件事实提取、文书起草四类任务，各自独立配置超时与温度；
- **案件分析与文书导出**：案件事实结构化提取、风险提示，法律文书（起诉状/律师函/劳动仲裁申请书）由固定模板 + LLM 草稿组装，支持 Apache POI 导出 DOCX；
- **完整评测体系**：内置固定法律测试集、三方案运行编排、Reference Article Hit@5 指标与图表展示。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus、Spring Security、JWT |
| 前端 | Vue 3、TypeScript、Element Plus、Pinia、Axios、ECharts |
| 数据 | MySQL 8、Qdrant |
| 大模型 | DeepSeek API、智谱 embedding-3 |
| RAG | 按法条切分、Embedding、Top-K 召回、原文回查、混合排序、来源引用 |
| 文书 | Apache POI / DOCX |

## 主要功能

- **法律知识库**：文档/条款管理、向量化和同步；
- **法律智能咨询**：DeepSeek 问答与参考法律条文引用；
- **RAG 双链路**：Basic RAG 原问题检索；Enhanced RAG 增加查询理解和查询改写；
- **案件与文书**：案件事实结构化提取、风险提示、法律文书生成与 DOCX 导出；
- **效果评测**：固定法律测试集、三方案运行、检索命中评估及图表展示；
- **账号与管理**：登录、角色权限、咨询记录、后台管理。

## 系统架构与 RAG 检索流程

```text
法律条文 -> 按“第X条”切分 -> Embedding -> Qdrant 向量存储
                                           |
用户问题 -> (Enhanced: 查询理解/改写) -> 向量召回
                                           |
                           MySQL 法规原文回查 + 混合加权排序
                                           |
                            Top5 参考条款 -> DeepSeek -> 回答与引用
```

- 数据侧：法律文档入库后按“第X条”粒度切分，经智谱 embedding-3 向量化写入 Qdrant，MySQL 保存法规原文；
- 检索侧：用户问题（Enhanced 链路先做查询理解与改写）经向量召回 Top-K，再回查 MySQL 原文并做混合加权排序；
- 生成侧：Top5 参考条款送入 DeepSeek 生成带引用的回答，前端可查看每条引用的来源条款。

原项目实现中：Basic RAG 召回 Top10，Enhanced RAG 召回 Top100，
并使用向量相似度 0.70 / 关键词匹配 0.20 / 法律领域匹配 0.10 进行加权排序，
配合检索阈值判定。

## 目录结构

```text
legal-rag-ai-assistant/
├── backend/                  # Spring Boot 后端（Java 17）
│   ├── src/main/java/        # 控制器、服务、RAG 检索、LLM 编排、安全认证
│   ├── src/main/resources/   # application.yml（环境变量）、模板、配置示例
│   ├── src/test/java/        # 单元测试（JWT、检索命中指标、文书模板等）
│   ├── sql/                  # 数据库表结构（11 张表，仅 DDL）
│   └── pom.xml               # Maven 配置（含 Maven Wrapper）
├── frontend/                 # Vue 3 + TypeScript 前端
│   ├── src/views/            # 咨询、案件分析、文书、评测、后台管理等页面
│   ├── src/stores/           # Pinia 状态
│   └── package.json          # 前端依赖声明
├── docker/                   # Docker Compose（Qdrant）
├── .gitignore                # 忽略密钥、构建产物与本地数据
└── README.md
```

## 效果评测（原项目历史记录，未在此公开包中重新运行）

固定测试题覆盖劳动争议、房屋租赁、消费者权益三类业务场景，
每类 10 题；Direct LLM、Basic RAG 和 Enhanced RAG 各运行 30 次，共 90 条记录。

| 方案 | Reference Article Hit@5 |
| --- | ---: |
| Basic RAG | 25/30 = 83.3% |
| Enhanced RAG | 23/30 = 76.7% |
| Direct LLM | 不适用（无检索链路） |

本次记录表明查询改写**未必带来命中率提升**。部分题目的法条术语在改写后发生偏移，
后续可对法律实体做约束并进一步评估。Hit@5 为检索指标，**不是答案正确率**。
数值来自原项目提供的论文/评测材料，不是本次清理后的重测结果。

## 界面截图

> 待补充：本公开包未包含运行截图。在本机完成部署并确认无隐私信息后，
> 可将真实截图补充到本节（登录页、咨询问答、RAG 引用、评测图表等）。

## 运行环境

- JDK 17，Maven（项目包含 Maven Wrapper）
- Node.js 22 或符合 Vite 依赖要求的版本，npm
- MySQL 8、Docker（用于启动 Qdrant）
- 自行申请的 DeepSeek API Key 和智谱 Embedding API Key

**一、初始化 MySQL 数据结构**（共 11 张表，缺少原项目业务数据）：

```bash
mysql -u root -p < backend/sql/schema.sql
```

**二、启动 Qdrant**：

```bash
docker compose -f docker/docker-compose.yml up -d
```

**三、配置本地凭据**：

复制 `backend/src/main/resources/application-local.example.yml` 为同目录
`application-local.yml`，只编辑复制文件，填入数据库用户名/密码、JWT 随机密钥、
管理员初始密码、DeepSeek 和智谱 Key。`application-local.yml` 已被 `.gitignore` 忽略。

`application.yml` 中的敏感配置已改成环境变量引用，不再内嵌真实密钥。
可选择设置这些环境变量，而不是使用本地 YAML。

**四、启动后端**（在 `backend` 目录内）：

```bash
./mvnw test
./mvnw spring-boot:run
```

Windows 可使用 `mvnw.cmd`。默认服务端口 8080。

**五、启动前端**（在 `frontend` 目录内）：

```bash
npm ci
npm run dev
```

默认浏览器地址：`http://localhost:5173`。

> 注意：为了隐私和体积，已移除本地 Qdrant 向量数据库及法律资料数据。
> 需要自行导入有合法使用权的法律文档并重新生成向量，才可验证完整 RAG 效果。
> 配置外部服务前，相关 LLM 功能无法运行。本上传版进行了静态安全与结构检查，
> **并未重新完整部署、调用收费 API 或执行所有端到端测试**。

## 开源安全说明

公开仓库**不应**包含：真实 API Key、数据库密码、JWT Secret、用户咨询记录、
`.git/` 历史、向量数据库和未经授权的法律资料。密钥如果已经对外共享，应到服务商后台轮换。

本项目未主动添加开源许可证；如需使用其他来源的代码或数据，应遵守其各自的授权条件。
