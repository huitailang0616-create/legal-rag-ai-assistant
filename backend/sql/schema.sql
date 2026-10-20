-- ============================================================================
-- ai_legal 正式系统 Schema（Final-System-Closeout-A / SCHEMA_REPRODUCIBILITY_DOC）
-- ----------------------------------------------------------------------------
-- 用途：从空数据库创建当前正式系统所需的全部业务表（纯 DDL，零业务数据）。
-- 唯一事实来源：正式库 ai_legal 的 SHOW CREATE TABLE（2026-08-14 核对）。
--
-- 执行方式（需 root / 具 DDL 权限的管理员账号执行，应用账号仅有 DML 权限）：
--   mysql -u root -p < schema.sql
--
-- 注意：
--   1. 本文件不包含任何账号/密码/API key；创建应用账号见 README.md（占位符）。
--   2. evaluation_question 的 30 道固定评测题由应用首次启动幂等初始化（DataInitializer），
--      本文件只建表，不重复 INSERT（避免双重事实来源）。
--   3. 注释为干净 UTF-8；正式库中 6A 之前历史建表的列注释存在历史编码问题，
--      字段结构（列/类型/默认值/索引/CHECK）与正式库逐项一致。
--   4. 已剥离 AUTO_INCREMENT 起始值（正式库存在历史数据计数，全新部署从 1 开始）。
--   5. legal_article / legal_document / operation_log / sys_user 的表级 COMMENT 为
--      新增文档性注释（正式库对应表无表注释），不影响结构。
--
-- 验证记录（2026-08-14）：在临时库 ai_legal_schema_test 执行本文件后，与正式库
-- ai_legal 的 INFORMATION_SCHEMA 对账（表集合/字段/类型/默认值/索引/CHECK）完全一致；
-- 验证完成已删除临时库。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `ai_legal`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `ai_legal`;

-- ----------------------------------------------------------------------------
-- 用户表（模块1）
-- ----------------------------------------------------------------------------
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(256) NOT NULL COMMENT 'BCrypt hash',
  `real_name` varchar(64) DEFAULT '',
  `phone` varchar(20) DEFAULT '',
  `email` varchar(128) DEFAULT '',
  `role` enum('USER','ADMIN') NOT NULL DEFAULT 'USER',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=normal, 0=disabled',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

-- ----------------------------------------------------------------------------
-- 操作日志（模块1，技术审计）
-- ----------------------------------------------------------------------------
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT '',
  `operation` varchar(64) NOT NULL COMMENT 'operation type',
  `target` varchar(256) DEFAULT '' COMMENT 'operation target',
  `result` varchar(256) DEFAULT '' COMMENT 'operation result',
  `ip` varchar(64) DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

-- ----------------------------------------------------------------------------
-- 法律法规文档（模块3 知识库）
-- ----------------------------------------------------------------------------
CREATE TABLE `legal_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_name` varchar(256) NOT NULL COMMENT '法规/文件名称',
  `domain` varchar(64) NOT NULL COMMENT '领域',
  `document_type` varchar(32) NOT NULL COMMENT '类型: LAW/JUDICIAL_INTERPRETATION/CASE',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `source` varchar(256) DEFAULT '' COMMENT '来源说明',
  `valid_status` tinyint NOT NULL DEFAULT '1' COMMENT '1=有效, 0=失效',
  `article_count` int DEFAULT '0' COMMENT '物理条款数量',
  `vector_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '向量状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_domain` (`domain`),
  KEY `idx_valid_status` (`valid_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律法规文档';

CREATE TABLE `legal_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL COMMENT '关联 legal_document.id',
  `article_number` varchar(32) NOT NULL COMMENT '条款编号原文',
  `content` text NOT NULL COMMENT '条款正文',
  `domain` varchar(64) NOT NULL COMMENT '领域（冗余）',
  `source` varchar(256) DEFAULT '' COMMENT '来源（冗余）',
  `valid_status` tinyint NOT NULL DEFAULT '1' COMMENT '1=有效, 0=失效',
  `vector_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '向量状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_domain` (`domain`),
  KEY `idx_valid_status` (`valid_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律条款';

-- ----------------------------------------------------------------------------
-- 案件诉求分析（模块4，用户数据表范式：user_id + deleted + 无FK）
-- ----------------------------------------------------------------------------
CREATE TABLE `case_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID（sys_user.id，不建FK，遵守项目惯例）',
  `title` varchar(128) NOT NULL COMMENT '案件标题',
  `domain` varchar(32) NOT NULL COMMENT '领域: LABOR_DISPUTE / HOUSE_RENTAL / CONSUMER_RIGHTS',
  `case_facts` text NOT NULL COMMENT '案件事实描述',
  `claims` text COMMENT '诉求要点',
  `key_dates` text COMMENT '关键时间节点',
  `evidence_description` text COMMENT '证据情况描述',
  `counterparty_description` text COMMENT '相对方描述',
  `location_description` text COMMENT '地点描述',
  `amount` decimal(15,2) DEFAULT NULL COMMENT '涉及金额（元）',
  `contract_status` varchar(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '合同情况: SIGNED / ORAL / UNSIGNED / UNKNOWN',
  `analysis_status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / ANALYZED(4B/4C)',
  `extraction_json` json DEFAULT NULL COMMENT '4B LLM 结构化案情提取结果',
  `extraction_confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '4B提取结果是否已人工确认: 0未确认 1已确认',
  `risk_result_json` json DEFAULT NULL COMMENT '4B Java 风险规则结果',
  `analysis_result` text COMMENT '4C 可编辑分析结论',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_deleted` (`user_id`,`deleted`),
  CONSTRAINT `chk_case_analysis_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_case_analysis_contract_status` CHECK ((`contract_status` in ('SIGNED','ORAL','UNSIGNED','UNKNOWN'))),
  CONSTRAINT `chk_case_analysis_extraction_confirmed` CHECK ((`extraction_confirmed` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件诉求分析主表';

-- ----------------------------------------------------------------------------
-- 法律文书草稿（模块5）
-- ----------------------------------------------------------------------------
CREATE TABLE `document_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID（sys_user.id，不建FK，遵守项目惯例）',
  `case_analysis_id` bigint DEFAULT NULL COMMENT '关联案件ID（case_analysis.id，可空，不建FK）',
  `title` varchar(128) NOT NULL COMMENT '文书标题',
  `document_type` varchar(32) NOT NULL COMMENT '文书类型: LABOR_ARBITRATION_APPLICATION / CIVIL_COMPLAINT / DEMAND_LETTER',
  `domain` varchar(32) NOT NULL COMMENT '领域: LABOR_DISPUTE / HOUSE_RENTAL / CONSUMER_RIGHTS',
  `content` longtext COMMENT '文书正文; NULL 表示未生成',
  `generation_status` varchar(20) NOT NULL DEFAULT 'NOT_GENERATED' COMMENT 'NOT_GENERATED(5A); 5B扩展GENERATING/GENERATED/GENERATION_FAILED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_deleted` (`user_id`,`deleted`),
  CONSTRAINT `chk_document_draft_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律文书草稿主表';

-- ----------------------------------------------------------------------------
-- 用户法律咨询历史（Final-System-Closeout-A）
-- 不可变快照：无更新 API；逻辑删除 deleted=1；citations_json 为后端生成引用数组
-- ----------------------------------------------------------------------------
CREATE TABLE `consultation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '咨询用户ID（sys_user.id，不建FK，遵守项目惯例）',
  `question` varchar(500) NOT NULL COMMENT '用户原始问题（与ConsultRequest @Size(500)一致）',
  `domain` varchar(32) NOT NULL COMMENT '检测领域: LABOR_DISPUTE/HOUSE_RENTAL/CONSUMER_RIGHTS/UNKNOWN',
  `answer` text NOT NULL COMMENT '系统回答（拒绝分支为固定话术，PASS为LLM回答）',
  `evidence_insufficient` tinyint NOT NULL DEFAULT 0 COMMENT '0=依据充分(PASS) 1=依据不足/拒绝分支',
  `relevance_reason` varchar(32) NOT NULL COMMENT 'PASS/KNOWLEDGE_GAP/NON_LEGAL_INTENT/UNSUPPORTED_DOMAIN',
  `citations_json` json NOT NULL COMMENT '后端生成引用数组（仅ConsultCitation六字段，空为[]）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_deleted_created` (`user_id`,`deleted`,`created_at`),
  CONSTRAINT `chk_consultation_history_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_consultation_history_evidence` CHECK ((`evidence_insufficient` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户法律咨询历史';

-- ----------------------------------------------------------------------------
-- 评测（模块6）：固定题目 / 评测批次 / 评测结果
-- ----------------------------------------------------------------------------
CREATE TABLE `evaluation_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_code` varchar(8) NOT NULL COMMENT 'L01-L10/R01-R10/C01-C10',
  `domain` varchar(32) NOT NULL COMMENT 'LABOR_DISPUTE/HOUSE_RENTAL/CONSUMER_RIGHTS',
  `question_text` text NOT NULL,
  `expected_evidence_keywords` text NOT NULL COMMENT 'JSON字符串数组：辅助关键词（历史兼容，不参与正式Hit@5）',
  `reference_articles_json` text COMMENT '人工标注参考法条JSON：[{"documentName":"...","articleNumber":"..."}]；REFERENCE_ARTICLE_TOP5_V2 命中判定依据；NULL=未标注',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_code` (`question_code`),
  KEY `idx_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测固定题目';

CREATE TABLE `evaluation_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
  `metric_version` varchar(32) NOT NULL DEFAULT 'LEGACY_KEYWORD_V1' COMMENT '命中口径: LEGACY_KEYWORD_V1=旧关键词口径(历史run) / REFERENCE_ARTICLE_TOP5_V2=参考法条Hit@5',
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL COMMENT 'sys_user.id（不建FK，遵守项目惯例）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测批次';

CREATE TABLE `evaluation_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `mode` varchar(16) NOT NULL COMMENT 'DIRECT_LLM/BASIC_RAG/ENHANCED_RAG',
  `answer` mediumtext,
  `retrieval_hit` tinyint DEFAULT NULL COMMENT 'RAG方案=gate是否PASS；DIRECT_LLM=NULL（不参与命中率）',
  `retrieved_article_ids` text COMMENT 'JSON数组',
  `citations_json` text COMMENT 'JSON数组（articleId/documentName/articleNumber/snippet/source）',
  `response_time_ms` int DEFAULT NULL,
  `execution_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/REJECTED/FAILED',
  `error_code` varchar(64) DEFAULT NULL,
  `answer_relevance_score` tinyint DEFAULT NULL,
  `legal_correctness_score` tinyint DEFAULT NULL,
  `citation_completeness_score` tinyint DEFAULT NULL,
  `hallucination_score` tinyint DEFAULT NULL COMMENT '1=无明显幻觉 -> 5=严重幻觉（方向与其他三维相反）',
  `manual_comment` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_question_mode` (`run_id`,`question_id`,`mode`),
  KEY `idx_mode` (`mode`),
  CONSTRAINT `chk_citation` CHECK (((`citation_completeness_score` is null) or (`citation_completeness_score` between 1 and 5))),
  CONSTRAINT `chk_halluc` CHECK (((`hallucination_score` is null) or (`hallucination_score` between 1 and 5))),
  CONSTRAINT `chk_legal` CHECK (((`legal_correctness_score` is null) or (`legal_correctness_score` between 1 and 5))),
  CONSTRAINT `chk_relevance` CHECK (((`answer_relevance_score` is null) or (`answer_relevance_score` between 1 and 5)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测结果';

-- ----------------------------------------------------------------------------
-- 正式法律咨询运行记录（Final-Requirement-Gaps-B / consult_runtime_record）
-- 管理员遥测表：POST /api/consult 每次服务级执行一行，终态一次性写入，无更新/删除。
-- 与 consultation_history 分工：history 只存用户得到的回答（SUCCESS/业务 REJECTED），
-- 本表存全部执行（含 Provider/QU/Answer FAILED）。记录写入失败不影响咨询结果。
-- retrieved_article_ids：JSON 数组（articleId，证据块顺序，即最终用于回答的 Top5）。
-- citations_json：ConsultCitation 六字段数组
--   (articleId/documentId/documentName/articleNumber/source/contentSnippet)，
--   拒绝/失败为空 []；contentSnippet <= 200 字符。
-- 验证记录：2026-08-15 在临时库 ai_legal_schema_test 执行本文件后与正式库
--   INFORMATION_SCHEMA 对账（表集合/字段/类型/默认值/索引/CHECK）一致后删除临时库。
-- ----------------------------------------------------------------------------
CREATE TABLE `consult_runtime_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '咨询用户ID（sys_user.id，不建FK，遵守项目惯例）',
  `username` varchar(64) DEFAULT '' COMMENT '用户名快照（操作日志同款，便于管理端直显）',
  `question` varchar(500) NOT NULL COMMENT '用户原始问题（与ConsultRequest @Size(500)一致）',
  `domain` varchar(32) NOT NULL COMMENT 'LABOR_DISPUTE/HOUSE_RENTAL/CONSUMER_RIGHTS/UNKNOWN（与history.detectedDomain同源）',
  `retrieval_mode` varchar(16) NOT NULL COMMENT '实际执行路径: ENHANCED_RAG/BASIC_FALLBACK/NO_RETRIEVAL',
  `fallback_reason` varchar(64) DEFAULT NULL COMMENT 'QU失败回退原因（如 QU_FAILURE(429)/QU_FAILURE:TIMEOUT），仅回退时写',
  `relevance_reason` varchar(32) DEFAULT NULL COMMENT 'PASS/NON_LEGAL_INTENT/UNSUPPORTED_DOMAIN/KNOWLEDGE_GAP；FAILED为NULL',
  `retrieved_article_ids` text COMMENT 'JSON数组：最终Top5 articleId（证据块顺序）；未检索/检索失败为NULL',
  `citations_json` text NOT NULL COMMENT '后端生成引用数组（articleId/documentId/documentName/articleNumber/source/contentSnippet六字段）；拒绝/失败为空[]',
  `answer` text COMMENT 'SUCCESS=LLM回答；REJECTED=固定话术；FAILED=NULL',
  `response_time_ms` int NOT NULL COMMENT 'service端到端耗时（QU+RAG+Answer+落库；失败路径到抛出点）',
  `execution_status` varchar(16) NOT NULL COMMENT 'SUCCESS/REJECTED/FAILED（终态一次性写入）',
  `error_code` varchar(64) DEFAULT NULL COMMENT 'FAILED类别码: QU_ERROR/EMBEDDING_ERROR/QDRANT_ERROR/RETRIEVAL_DB_ERROR/RATE_LIMIT/TIMEOUT/PROVIDER_ERROR/HISTORY_SAVE_ERROR/INTERNAL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`,`created_at`),
  KEY `idx_status_created` (`execution_status`,`created_at`),
  CONSTRAINT `chk_runtime_execution_status` CHECK ((`execution_status` in (_utf8mb4'SUCCESS',_utf8mb4'REJECTED',_utf8mb4'FAILED'))),
  CONSTRAINT `chk_runtime_retrieval_mode` CHECK ((`retrieval_mode` in (_utf8mb4'ENHANCED_RAG',_utf8mb4'BASIC_FALLBACK',_utf8mb4'NO_RETRIEVAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='正式法律咨询运行记录';
