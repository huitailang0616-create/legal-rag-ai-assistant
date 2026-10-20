package com.ailegal.service.impl;

import com.ailegal.dto.ConsultCitation;
import com.ailegal.dto.EvaluationResultView;
import com.ailegal.dto.EvaluationRunDetail;
import com.ailegal.dto.ExecuteNextResponse;
import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.dto.ModeSummary;
import com.ailegal.dto.QueryUnderstandingResult;
import com.ailegal.dto.RagRetrieveTestResponse;
import com.ailegal.dto.RagTop5Hit;
import com.ailegal.dto.RunSummaryResponse;
import com.ailegal.dto.ScoreRequest;
import com.ailegal.entity.EvaluationQuestion;
import com.ailegal.entity.EvaluationResult;
import com.ailegal.entity.EvaluationRun;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.EvaluationQuestionMapper;
import com.ailegal.mapper.EvaluationResultMapper;
import com.ailegal.mapper.EvaluationRunMapper;
import com.ailegal.service.EvaluationExecutionService;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.LlmService;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.QueryUnderstandingService;
import com.ailegal.service.RagService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块6B 评测执行与统计实现。
 *
 * 执行模型（Pro 审查确认）：
 *   executeNext 由多个短事务 + 一次真实 LLM/检索执行组成，绝不在事务内持有
 *   数据库锁跨 LLM 调用：
 *     事务1（短）: 回收 stale RUNNING（>10min 重置为 PENDING）
 *                  -> SELECT ... FOR UPDATE 抢占一条 PENDING 结果并标记 RUNNING
 *                  -> run 条件迁移 PENDING -> RUNNING（首次执行）
 *     执行     （无事务）: 按 mode 跑真实管线（QU/RAG/LLM），捕获一切异常 -> FAILED
 *     事务2（短）: 条件 UPDATE 落终态（affected=0 让出）
 *                  -> 剩余 0 时条件迁移 run RUNNING -> COMPLETED
 *
 * 统计与 CSV 均为 Java 侧聚合（90 行 selectList + streams），不新增 SQL 聚合函数。
 */
@Service
public class EvaluationExecutionServiceImpl implements EvaluationExecutionService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationExecutionServiceImpl.class);

    public static final String MODE_DIRECT_LLM = "DIRECT_LLM";
    public static final String MODE_BASIC_RAG = "BASIC_RAG";
    public static final String MODE_ENHANCED_RAG = "ENHANCED_RAG";

    private static final List<String> MODE_ORDER = List.of(MODE_DIRECT_LLM, MODE_BASIC_RAG, MODE_ENHANCED_RAG);

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_FAILED = "FAILED";

    /** 命中口径版本：历史 run = LEGACY_KEYWORD_V1（旧关键词定义）；新 run = REFERENCE_ARTICLE_TOP5_V2 */
    public static final String METRIC_LEGACY_KEYWORD_V1 = "LEGACY_KEYWORD_V1";
    public static final String METRIC_REFERENCE_TOP5_V2 = "REFERENCE_ARTICLE_TOP5_V2";

    /** 参考法条解析失败稳定错误码（V2 口径）：不判 miss，retrieval_hit=NULL，不进命中率分母 */
    public static final String ERROR_REF_NOT_FOUND = "REFERENCE_ARTICLE_NOT_FOUND";
    public static final String ERROR_REF_AMBIGUOUS = "REFERENCE_ARTICLE_AMBIGUOUS";

    /** stale 阈值：RUNNING 超过 10 分钟视为崩溃残留 */
    private static final int STALE_MINUTES = 10;
    private static final int SNIPPET_MAX = 200;

    private static final String OP_RUN_CREATE = "EVALUATION_RUN_CREATE";
    private static final String OP_SCORE_UPDATE = "EVALUATION_SCORE_UPDATE";
    private static final String OP_CSV_EXPORT = "EVALUATION_CSV_EXPORT";
    private static final String OP_RUN_COMPLETE = "EVALUATION_RUN_COMPLETE";

    /**
     * Canonical domain terms —— 与 ConsultServiceImpl（模块3，private）镜像的三条常量。
     * 仅用于 ENHANCED 的检索 query 前缀，与 6A 冻结定义一致。
     */
    private static final Map<String, String> CANONICAL_DOMAIN_TERM = Map.of(
            "LABOR_DISPUTE", "劳动争议",
            "HOUSE_RENTAL", "房屋租赁",
            "CONSUMER_RIGHTS", "消费者权益"
    );

    /**
     * BASIC / ENHANCED 共享的 grounded 评测 prompt（Pro 提供草稿，模块6 常量）。
     * 与模块3 的 SYSTEM_PROMPT 同一 grounding 规则族。
     */
    private static final String GROUNDED_EVAL_SYSTEM_PROMPT =
            "你是中国法律信息咨询辅助系统，不是律师。\n"
                    + "\n"
                    + "回答规则：\n"
                    + "1. 只能依据本次对话提供的【法律依据】回答，不得使用依据之外的规则、法条编号、判例、司法解释、数字或法律结论。\n"
                    + "2. 如果现有依据无法支持问题中的某个具体结论，必须明确说明“根据本次提供的法律依据，暂不足以直接判断这一点”，不得用自身知识补齐。\n"
                    + "3. 不得声称“保证胜诉”“法院一定支持”“你一定能获得赔偿”等确定性结果。\n"
                    + "4. 不得提“检索”“知识库”“Top-5”“RAG”等系统内部概念，保持正常用户语言。\n"
                    + "5. 不得输出 JSON，直接用中文自然语言回答，普通用户能读懂。\n"
                    + "6. 结尾必须包含免责声明：“以上内容仅供法律信息参考，不构成正式法律意见。”\n"
                    + "7. 你拥有的模型训练知识不能作为本次回答依据；即使某条中国法律规则在常识上正确，只要未出现在本次提供的【法律依据】中，就不得写入回答。\n"
                    + "8. 对于赔偿金额、违约金比例、违约金调整、具体期限、法定倍数等具体规则，必须能在本次【法律依据】文本中找到直接支持，否则不得给出该规则。";

    /**
     * DIRECT_LLM 评测 prompt（Pro 提供草稿）：允许通用法律知识，但必须诚实。
     */
    private static final String DIRECT_EVAL_SYSTEM_PROMPT =
            "你是中国法律信息咨询辅助系统，不是律师。\n"
                    + "\n"
                    + "回答规则：\n"
                    + "1. 依据你的通用中国法律知识回答，面向普通用户，清楚易懂。\n"
                    + "2. 对不确定的内容，必须明确说明“这一点我不确定”，不要猜测。\n"
                    + "3. 不得编造法条编号、具体数字、机构名称或程序期限。\n"
                    + "4. 不得声称“我检索了法律条文”或类似检索行为。\n"
                    + "5. 不得输出 JSON，直接用中文自然语言回答。\n"
                    + "6. 结尾必须包含免责声明：“以上内容仅供法律信息参考，不构成正式法律意见。”";

    private final EvaluationRunMapper evaluationRunMapper;
    private final EvaluationQuestionMapper evaluationQuestionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final OperationLogService operationLogService;
    private final QueryUnderstandingService queryUnderstandingService;
    private final RagService ragService;
    private final LegalKnowledgeService legalKnowledgeService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public EvaluationExecutionServiceImpl(EvaluationRunMapper evaluationRunMapper,
                                          EvaluationQuestionMapper evaluationQuestionMapper,
                                          EvaluationResultMapper evaluationResultMapper,
                                          OperationLogService operationLogService,
                                          QueryUnderstandingService queryUnderstandingService,
                                          RagService ragService,
                                          LegalKnowledgeService legalKnowledgeService,
                                          LlmService llmService,
                                          ObjectMapper objectMapper,
                                          PlatformTransactionManager transactionManager) {
        this.evaluationRunMapper = evaluationRunMapper;
        this.evaluationQuestionMapper = evaluationQuestionMapper;
        this.evaluationResultMapper = evaluationResultMapper;
        this.operationLogService = operationLogService;
        this.queryUnderstandingService = queryUnderstandingService;
        this.ragService = ragService;
        this.legalKnowledgeService = legalKnowledgeService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- createRun

    @Override
    @Transactional
    public EvaluationRun createRun(String name, Long userId, String username, String ip) {
        // 活跃 run 限制：同一时刻只允许一个 PENDING/RUNNING 批次
        Long active = evaluationRunMapper.selectCount(new LambdaQueryWrapper<EvaluationRun>()
                .in(EvaluationRun::getStatus, "PENDING", "RUNNING"));
        if (active != null && active > 0) {
            throw new BusinessException(409, "存在进行中的评测批次，请等待其结束或完成后重试");
        }

        EvaluationRun run = new EvaluationRun();
        run.setName(name);
        run.setStatus("PENDING");
        run.setMetricVersion(METRIC_REFERENCE_TOP5_V2);
        run.setCreatedBy(userId);
        run.setCreatedAt(LocalDateTime.now());
        evaluationRunMapper.insert(run);

        List<EvaluationQuestion> questions = evaluationQuestionMapper.selectList(
                new LambdaQueryWrapper<EvaluationQuestion>().orderByAsc(EvaluationQuestion::getQuestionCode));
        for (EvaluationQuestion q : questions) {
            for (String mode : MODE_ORDER) {
                EvaluationResult r = new EvaluationResult();
                r.setRunId(run.getId());
                r.setQuestionId(q.getId());
                r.setMode(mode);
                r.setExecutionStatus("PENDING");
                r.setCreatedAt(LocalDateTime.now());
                r.setUpdatedAt(LocalDateTime.now());
                evaluationResultMapper.insert(r);
            }
        }

        safeLog(userId, username, OP_RUN_CREATE, String.valueOf(run.getId()),
                "name=" + name + ",questions=" + questions.size() + ",modes=3", ip);
        return run;
    }

    // ---------------------------------------------------------------- executeNext

    @Override
    public ExecuteNextResponse executeNext(Long runId, Long userId, String username, String ip) {
        EvaluationRun run = evaluationRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "评测批次不存在");
        }

        // ---- 事务1（短）：stale 回收 + 抢占 + run 首次启动 ----
        Long resultId;
        try {
            resultId = transactionTemplate.execute(status -> {
                // stale run 回收：RUNNING 超 10 分钟 -> PENDING（服务器崩溃残留）
                evaluationRunMapper.update(null, new UpdateWrapper<EvaluationRun>()
                        .eq("id", runId).eq("status", "RUNNING")
                        .lt("started_at", LocalDateTime.now().minusMinutes(STALE_MINUTES))
                        .set("status", "PENDING").set("started_at", null));
                // stale result 回收：RUNNING 超 10 分钟 -> PENDING（执行中途崩溃）
                evaluationResultMapper.update(null, new UpdateWrapper<EvaluationResult>()
                        .eq("run_id", runId).eq("execution_status", "RUNNING")
                        .lt("updated_at", LocalDateTime.now().minusMinutes(STALE_MINUTES))
                        .set("execution_status", "PENDING").set("updated_at", LocalDateTime.now()));
                // 抢占一条 PENDING（FOR UPDATE 行锁，短事务内立即提交）
                Long claimed = evaluationResultMapper.selectFirstPendingIdForUpdate(runId);
                if (claimed == null) {
                    return null;
                }
                int affected = evaluationResultMapper.update(null, new UpdateWrapper<EvaluationResult>()
                        .eq("id", claimed).eq("execution_status", "PENDING")
                        .set("execution_status", "RUNNING").set("updated_at", LocalDateTime.now()));
                if (affected == 0) {
                    return null;
                }
                // run 首次执行：PENDING -> RUNNING（条件迁移，一次生效）
                evaluationRunMapper.update(null, new UpdateWrapper<EvaluationRun>()
                        .eq("id", runId).eq("status", "PENDING")
                        .set("status", "RUNNING").set("started_at", LocalDateTime.now()));
                return claimed;
            });
        } catch (Exception e) {
            markRunFailed(runId);
            throw e;
        }
        if (resultId == null) {
            // 无 PENDING 可执行 -> 返回真实计数（可能有其他调用方正在执行 RUNNING 行）
            long inProgress = countInProgress(runId);
            EvaluationRun current = evaluationRunMapper.selectById(runId);
            return new ExecuteNextResponse(null, null,
                    current == null ? run.getStatus() : current.getStatus(), inProgress, countTotal(runId));
        }

        // ---- 执行（无事务）：真实管线，异常 -> FAILED 结果，绝不重试 Provider ----
        EvaluationResult result = evaluationResultMapper.selectById(resultId);
        EvaluationQuestion question = evaluationQuestionMapper.selectById(result.getQuestionId());
        ModeOutcome outcome;
        long t0 = System.currentTimeMillis();
        try {
            outcome = switch (result.getMode()) {
                case MODE_DIRECT_LLM -> runDirect(question, t0);
                case MODE_BASIC_RAG -> runBasic(question, t0, userId, username, ip);
                case MODE_ENHANCED_RAG -> runEnhanced(question, t0, userId, username, ip);
                default -> new ModeOutcome(STATUS_FAILED, "INTERNAL", "未知 mode: " + result.getMode(),
                        null, null, elapsed(t0), "[]");
            };
        } catch (Throwable t) {
            log.error("executeNext 未预期异常 runId={} resultId={}", runId, resultId, t);
            outcome = new ModeOutcome(STATUS_FAILED, "INTERNAL", shortMessage(t),
                    null, null, elapsed(t0), "[]");
        }

        // ---- 事务2（短）：落终态 + COMPLETED 判定 ----
        ModeOutcome finalOutcome = outcome;
        try {
            return transactionTemplate.execute(status -> {
                evaluationResultMapper.update(null, new UpdateWrapper<EvaluationResult>()
                        .eq("id", resultId).eq("execution_status", "RUNNING")
                        .set("execution_status", finalOutcome.executionStatus)
                        .set("answer", finalOutcome.answer)
                        .set("retrieval_hit", finalOutcome.retrievalHit)
                        .set("retrieved_article_ids", finalOutcome.retrievedArticleIds)
                        .set("citations_json", finalOutcome.citationsJson)
                        .set("response_time_ms", finalOutcome.responseTimeMs)
                        .set("error_code", finalOutcome.errorCode)
                        .set("updated_at", LocalDateTime.now()));

                long pending = countInProgress(runId);
                if (pending == 0) {
                    int runAffected = evaluationRunMapper.update(null, new UpdateWrapper<EvaluationRun>()
                            .eq("id", runId).eq("status", "RUNNING")
                            .set("status", "COMPLETED").set("finished_at", LocalDateTime.now()));
                    if (runAffected > 0) {
                        safeLog(userId, username, OP_RUN_COMPLETE, String.valueOf(runId),
                                "total=" + countTotal(runId) + ",mode=ALL", ip);
                    }
                }
                EvaluationRun current = evaluationRunMapper.selectById(runId);
                return new ExecuteNextResponse(resultId, finalOutcome.executionStatus,
                        current == null ? "UNKNOWN" : current.getStatus(),
                        pending, countTotal(runId));
            });
        } catch (Exception e) {
            markRunFailed(runId);
            throw e;
        }
    }

    // ---------------------------------------------------------------- mode pipelines

    private ModeOutcome runDirect(EvaluationQuestion question, long t0) {
        try {
            String answer = llmService.chat(DIRECT_EVAL_SYSTEM_PROMPT,
                    "【用户问题】\n" + question.getQuestionText());
            return new ModeOutcome(STATUS_SUCCESS, null, answer,
                    null, null, elapsed(t0), null);
        } catch (BusinessException e) {
            return failure(e, t0);
        }
    }

    private ModeOutcome runBasic(EvaluationQuestion question, long t0, Long userId, String username, String ip) {
        RagRetrieveTestResponse retrieval;
        try {
            // BASIC：原问题直接检索（6A 冻结定义，keywords 绝不进入 query）
            retrieval = ragService.retrieveTest(question.getQuestionText(), userId, username, ip);
        } catch (BusinessException e) {
            return failure(e, t0);
        }
        return runGrounded(question, retrieval, t0, userId, username, ip);
    }

    private ModeOutcome runEnhanced(EvaluationQuestion question, long t0, Long userId, String username, String ip) {
        QueryUnderstandingResult qu;
        try {
            qu = queryUnderstandingService.understand(question.getQuestionText());
        } catch (BusinessException e) {
            return failure(e, t0);
        }
        if (!qu.isLegalIntent()) {
            return new ModeOutcome(STATUS_REJECTED, null, "NON_LEGAL_INTENT: 问题被判定为非法律咨询",
                    null, null, elapsed(t0), "[]");
        }
        if (QueryUnderstandingResult.UNKNOWN.equals(qu.getSupportedDomain())) {
            return new ModeOutcome(STATUS_REJECTED, null,
                    "UNSUPPORTED_DOMAIN: 超出劳动争议/房屋租赁/消费者权益范围",
                    null, null, elapsed(t0), "[]");
        }
        String retrievalQuery = CANONICAL_DOMAIN_TERM.get(qu.getSupportedDomain()) + " " + qu.getRewrittenQuery();
        RagRetrieveTestResponse retrieval;
        try {
            retrieval = ragService.retrieveEnhanced(retrievalQuery, userId, username, ip);
        } catch (BusinessException e) {
            return failure(e, t0);
        }
        return runGrounded(question, retrieval, t0, userId, username, ip);
    }

    /** BASIC/ENHANCED 共享：gate 判定 -> 证据加载 -> grounded answer（与模块3 同一规则族） */
    private ModeOutcome runGrounded(EvaluationQuestion question, RagRetrieveTestResponse retrieval,
                                    long t0, Long userId, String username, String ip) {
        List<RagTop5Hit> top5 = retrieval.getTop5() == null ? List.of() : retrieval.getTop5();
        String retrievedIds = joinArticleIds(top5);
        List<Long> top5Ids = top5.stream().map(RagTop5Hit::getArticleId).toList();
        List<LegalArticleDocument> ordered = loadOrdered(top5);

        if (!retrieval.isRelevancePassed()) {
            // gate reject：合法结果 REJECTED；Top5 已填充 -> 仍判定命中（V2：参考法条 vs final Top5）
            HitVerdict v = judgeReferenceHit(question, top5Ids);
            String reason = retrieval.getRelevanceReason() == null ? "LOW_RELEVANCE" : retrieval.getRelevanceReason();
            return new ModeOutcome(STATUS_REJECTED, v.errorCode,
                    "KNOWLEDGE_GAP: " + reason, v.hit, retrievedIds, elapsed(t0), "[]");
        }
        if (ordered.isEmpty()) {
            // gate PASS 但 MySQL 无有效条款（条款被删等极端情况）；Top5 已形成仍可判定
            HitVerdict v = judgeReferenceHit(question, top5Ids);
            return new ModeOutcome(STATUS_REJECTED, v.errorCode,
                    "KNOWLEDGE_GAP: NO_RESULT", v.hit, retrievedIds, elapsed(t0), "[]");
        }

        String evidence = buildEvidenceBlock(ordered);
        String answer;
        try {
            answer = llmService.chat(GROUNDED_EVAL_SYSTEM_PROMPT,
                    buildUserPrompt(question.getQuestionText(), evidence));
        } catch (BusinessException e) {
            // 检索已成功、证据已拿到：命中照常判定（冻结定义：仅 QU reject / 检索失败 为 NULL）
            ModeOutcome f = failure(e, t0);
            HitVerdict v = judgeReferenceHit(question, top5Ids);
            f.retrievalHit = v.hit;
            if (v.errorCode != null) {
                f.errorCode = v.errorCode;
            }
            f.retrievedArticleIds = retrievedIds;
            return f;
        }
        HitVerdict v = judgeReferenceHit(question, top5Ids);
        return new ModeOutcome(STATUS_SUCCESS, v.errorCode, answer,
                v.hit, retrievedIds, elapsed(t0), buildCitationsJson(ordered));
    }

    // ---------------------------------------------------------------- helpers

    private ModeOutcome failure(BusinessException e, long t0) {
        return new ModeOutcome(STATUS_FAILED, mapError(e), shortMessage(e),
                null, null, elapsed(t0), "[]");
    }

    private static String mapError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "INTERNAL";
        }
        if (msg.contains("问题理解失败")) return "QU_ERROR";
        if (msg.contains("检索失败:Embedding")) return "EMBEDDING_ERROR";
        if (msg.contains("检索失败:Qdrant")) return "QDRANT_ERROR";
        if (msg.contains("检索失败:MySQL")) return "RETRIEVAL_DB_ERROR";
        if (msg.contains("(429)")) return "RATE_LIMIT";
        if (msg.contains("超时")) return "TIMEOUT";
        if (e instanceof BusinessException) return "PROVIDER_ERROR";
        return "INTERNAL";
    }

    /**
     * 正式命中口径 REFERENCE_ARTICLE_TOP5_V2（Gaps-E）：
     * 题目人工标注的参考法条（documentName+articleNumber 稳定标识）解析为当前有效 legal_article.id，
     * 与 final authoritative Top5 article ids 精确集合比较：存在至少一个交集 -> hit=1，无交集 -> 0。
     * 严禁 substring/LIKE/keywordContains/contentContains/LLM 判断参与。
     * expected_evidence_keywords 完全退出 hit 判定路径（保留为辅助描述/历史兼容）。
     * 参考法条解析失败（0 条=NOT_FOUND / 多条=AMBIGUOUS）-> hit=NULL + 稳定错误码，不判 miss。
     */
    private HitVerdict judgeReferenceHit(EvaluationQuestion question, List<Long> top5ArticleIds) {
        List<ReferenceKey> refs = parseReferences(question.getReferenceArticlesJson());
        if (refs.isEmpty()) {
            // 未标注：无法判定（防御；Gaps-E 验收保证 30/30 已标注）
            return new HitVerdict(null, null);
        }
        Map<String, List<Long>> resolved = resolveReferenceIds(refs);
        Set<Long> referenceIds = new HashSet<>();
        for (ReferenceKey key : refs) {
            List<Long> ids = resolved.get(key.key());
            if (ids == null || ids.isEmpty()) {
                return new HitVerdict(null, ERROR_REF_NOT_FOUND);
            }
            if (ids.size() > 1) {
                return new HitVerdict(null, ERROR_REF_AMBIGUOUS);
            }
            referenceIds.add(ids.get(0));
        }
        if (top5ArticleIds.isEmpty()) {
            // 检索成功但 final Top5 为空（无候选）-> 参考法条不可能命中，记为 miss
            return new HitVerdict(0, null);
        }
        for (Long id : top5ArticleIds) {
            if (referenceIds.contains(id)) {
                return new HitVerdict(1, null);
            }
        }
        return new HitVerdict(0, null);
    }

    /** 解析 question.referenceArticlesJson -> [{"documentName","articleNumber"}]；非法/空 -> 空列表 */
    private List<ReferenceKey> parseReferences(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ReferenceKey> refs = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ReferenceKey.class));
            return refs == null ? List.of() : refs.stream()
                    .filter(r -> r.documentName() != null && !r.documentName().isBlank()
                            && r.articleNumber() != null && !r.articleNumber().isBlank())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 按 (document_name, article_number) 二元组解析为当前有效 articleId；重复/缺失由调用方判定 */
    private Map<String, List<Long>> resolveReferenceIds(List<ReferenceKey> refs) {
        List<String> names = refs.stream().map(ReferenceKey::documentName).distinct().toList();
        List<String> numbers = refs.stream().map(ReferenceKey::articleNumber).distinct().toList();
        List<LegalArticleDocument> rows = legalKnowledgeService.selectValidReferenceIds(names, numbers);
        Map<String, List<Long>> byKey = new HashMap<>();
        for (LegalArticleDocument row : rows) {
            String key = ReferenceKey.key(row.getDocumentName(), row.getArticleNumber());
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row.getArticleId());
        }
        return byKey;
    }

    /** 参考法条稳定标识：documentName + articleNumber（不保存 DB 自增 articleId，避免 schema 重建漂移） */
    record ReferenceKey(String documentName, String articleNumber) {
        String key() {
            return key(documentName, articleNumber);
        }
        static String key(String documentName, String articleNumber) {
            return documentName + "::" + articleNumber;
        }
    }

    /** Hit 判定结果：hit=NULL 表示无法判定（未标注/解析失败），errorCode 为稳定错误码 */
    private static final class HitVerdict {
        final Integer hit;
        final String errorCode;

        HitVerdict(Integer hit, String errorCode) {
            this.hit = hit;
            this.errorCode = errorCode;
        }
    }

    /** 保持 Top5 排名顺序的 MySQL 权威数据加载（LegalArticleMapper JOIN 无 ORDER BY） */
    private List<LegalArticleDocument> loadOrdered(List<RagTop5Hit> top5) {
        List<Long> ids = top5.stream().map(RagTop5Hit::getArticleId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<LegalArticleDocument> articles = legalKnowledgeService.selectValidArticlesWithDocuments(ids);
        Map<Long, LegalArticleDocument> byId = new HashMap<>();
        for (LegalArticleDocument a : articles) {
            byId.put(a.getArticleId(), a);
        }
        List<LegalArticleDocument> ordered = new ArrayList<>();
        for (Long id : ids) {
            LegalArticleDocument a = byId.get(id);
            if (a != null) {
                ordered.add(a);
            }
        }
        return ordered;
    }

    private String joinArticleIds(List<RagTop5Hit> top5) {
        List<String> ids = top5.stream().map(h -> String.valueOf(h.getArticleId())).toList();
        if (ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildEvidenceBlock(List<LegalArticleDocument> ordered) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (LegalArticleDocument a : ordered) {
            sb.append("【依据").append(idx).append("】\n");
            sb.append("法规：").append(nvl(a.getDocumentName())).append("\n");
            sb.append("条款：").append(nvl(a.getArticleNumber())).append("\n");
            sb.append("正文：").append(nvl(a.getContent())).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    private static String buildUserPrompt(String question, String evidence) {
        return evidence + "【用户问题】\n" + question + "\n\n"
                + "回答时可以引用 [依据1]、[依据2] 这样的编号。";
    }

    private String buildCitationsJson(List<LegalArticleDocument> ordered) {
        try {
            List<ConsultCitation> citations = new ArrayList<>();
            for (LegalArticleDocument a : ordered) {
                String content = nvl(a.getContent());
                String snippet = content.length() <= SNIPPET_MAX ? content : content.substring(0, SNIPPET_MAX);
                citations.add(new ConsultCitation(a.getArticleId(), a.getDocumentId(),
                        a.getDocumentName(), a.getArticleNumber(), a.getSource(), snippet));
            }
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void requireRun(Long runId) {
        if (evaluationRunMapper.selectById(runId) == null) {
            throw new BusinessException(404, "评测批次不存在");
        }
    }

    private void markRunFailed(Long runId) {
        try {
            evaluationRunMapper.update(null, new UpdateWrapper<EvaluationRun>()
                    .eq("id", runId).in("status", "PENDING", "RUNNING")
                    .set("status", "FAILED").set("finished_at", LocalDateTime.now()));
        } catch (Exception ignore) {
            // 尽力而为
        }
    }

    private long countInProgress(Long runId) {
        Long c = evaluationResultMapper.selectCount(new LambdaQueryWrapper<EvaluationResult>()
                .eq(EvaluationResult::getRunId, runId)
                .in(EvaluationResult::getExecutionStatus, "PENDING", "RUNNING"));
        return c == null ? 0 : c;
    }

    private long countTotal(Long runId) {
        Long c = evaluationResultMapper.selectCount(new LambdaQueryWrapper<EvaluationResult>()
                .eq(EvaluationResult::getRunId, runId));
        return c == null ? 0 : c;
    }

    private static long elapsed(long t0) {
        return System.currentTimeMillis() - t0;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String shortMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private void safeLog(Long userId, String username, String op, String target, String result, String ip) {
        try {
            operationLogService.log(userId, username, op, target, result, ip);
        } catch (Exception e) {
            log.warn("评测日志写入失败 op={}", op, e);
        }
    }

    // ---------------------------------------------------------------- queries

    @Override
    public EvaluationRunDetail getRunDetail(Long runId) {
        EvaluationRun run = evaluationRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "评测批次不存在");
        }
        List<EvaluationResult> results = evaluationResultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResult>().eq(EvaluationResult::getRunId, runId));
        long pending = 0, running = 0, success = 0, rejected = 0, failed = 0;
        for (EvaluationResult r : results) {
            switch (r.getExecutionStatus()) {
                case "PENDING" -> pending++;
                case "RUNNING" -> running++;
                case "SUCCESS" -> success++;
                case "REJECTED" -> rejected++;
                case "FAILED" -> failed++;
                default -> { }
            }
        }
        EvaluationRunDetail detail = new EvaluationRunDetail();
        detail.setRun(run);
        detail.setTotal(results.size());
        detail.setPending(pending);
        detail.setRunning(running);
        detail.setSuccess(success);
        detail.setRejected(rejected);
        detail.setFailed(failed);
        return detail;
    }

    @Override
    public List<EvaluationResultView> listResults(Long runId) {
        requireRun(runId);
        String metricVersion = evaluationRunMapper.selectById(runId).getMetricVersion();
        List<EvaluationResult> results = evaluationResultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResult>()
                        .eq(EvaluationResult::getRunId, runId)
                        .orderByAsc(EvaluationResult::getId));
        Map<Long, EvaluationQuestion> qmap = new HashMap<>();
        List<Long> qids = results.stream().map(EvaluationResult::getQuestionId).distinct().toList();
        if (!qids.isEmpty()) {
            for (EvaluationQuestion q : evaluationQuestionMapper.selectBatchIds(qids)) {
                qmap.put(q.getId(), q);
            }
        }
        List<EvaluationResultView> views = new ArrayList<>();
        for (EvaluationResult r : results) {
            EvaluationQuestion q = qmap.get(r.getQuestionId());
            EvaluationResultView v = new EvaluationResultView();
            v.setId(r.getId());
            v.setQuestionCode(q == null ? "-" : q.getQuestionCode());
            v.setDomain(q == null ? "-" : q.getDomain());
            v.setQuestion(q == null ? "-" : q.getQuestionText());
            v.setMode(r.getMode());
            v.setAnswer(r.getAnswer());
            v.setRetrievalHit(r.getRetrievalHit());
            v.setRetrievedArticleIds(r.getRetrievedArticleIds());
            v.setCitationsJson(r.getCitationsJson());
            v.setReferenceArticlesJson(q == null ? null : q.getReferenceArticlesJson());
            v.setMetricVersion(metricVersion);
            v.setResponseTimeMs(r.getResponseTimeMs());
            v.setExecutionStatus(r.getExecutionStatus());
            v.setErrorCode(r.getErrorCode());
            v.setAnswerRelevanceScore(r.getAnswerRelevanceScore());
            v.setLegalCorrectnessScore(r.getLegalCorrectnessScore());
            v.setCitationCompletenessScore(r.getCitationCompletenessScore());
            v.setHallucinationScore(r.getHallucinationScore());
            v.setManualComment(r.getManualComment());
            views.add(v);
        }
        return views;
    }

    @Override
    public RunSummaryResponse getSummary(Long runId) {
        requireRun(runId);
        List<EvaluationResult> results = evaluationResultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResult>().eq(EvaluationResult::getRunId, runId));
        Map<String, ModeSummary> byMode = new LinkedHashMap<>();
        for (String mode : MODE_ORDER) {
            byMode.put(mode, new ModeSummary());
            byMode.get(mode).setMode(mode);
        }
        for (EvaluationResult r : results) {
            ModeSummary m = byMode.get(r.getMode());
            if (m == null) {
                m = new ModeSummary();
                m.setMode(r.getMode());
                byMode.put(r.getMode(), m);
            }
            m.setTotal(m.getTotal() + 1);
            switch (r.getExecutionStatus()) {
                case STATUS_SUCCESS -> m.setSuccess(m.getSuccess() + 1);
                case STATUS_REJECTED -> m.setRejected(m.getRejected() + 1);
                case STATUS_FAILED -> m.setFailed(m.getFailed() + 1);
                default -> { }
            }
            boolean terminal = STATUS_SUCCESS.equals(r.getExecutionStatus())
                    || STATUS_REJECTED.equals(r.getExecutionStatus());
            if (terminal && r.getResponseTimeMs() != null) {
                m.setAvgResponseTimeMs(m.getAvgResponseTimeMs() == null
                        ? r.getResponseTimeMs().longValue()
                        : m.getAvgResponseTimeMs() + r.getResponseTimeMs());
                m.setResponseTimeSampleCount(m.getResponseTimeSampleCount() + 1);
            }
            if (!MODE_DIRECT_LLM.equals(r.getMode()) && r.getRetrievalHit() != null) {
                m.setRetrievalEvaluatedCount(m.getRetrievalEvaluatedCount() + 1);
                if (r.getRetrievalHit() == 1) {
                    m.setRetrievalHitCount(m.getRetrievalHitCount() + 1);
                }
            }
            addScore(m, r.getAnswerRelevanceScore(), ScoreField.ANSWER_RELEVANCE);
            addScore(m, r.getLegalCorrectnessScore(), ScoreField.LEGAL_CORRECTNESS);
            addScore(m, r.getCitationCompletenessScore(), ScoreField.CITATION_COMPLETENESS);
            addScore(m, r.getHallucinationScore(), ScoreField.HALLUCINATION);
        }
        List<ModeSummary> modes = new ArrayList<>();
        for (String mode : MODE_ORDER) {
            ModeSummary m = byMode.get(mode);
            if (m == null) {
                continue;
            }
            if (m.getResponseTimeSampleCount() > 0) {
                m.setAvgResponseTimeMs(m.getAvgResponseTimeMs() / m.getResponseTimeSampleCount());
            } else {
                m.setAvgResponseTimeMs(null);
            }
            if (m.getRetrievalEvaluatedCount() > 0) {
                m.setRetrievalHitRate((double) m.getRetrievalHitCount() / m.getRetrievalEvaluatedCount());
            } else {
                m.setRetrievalHitRate(null);
            }
            modes.add(m);
        }
        return new RunSummaryResponse(runId, modes);
    }

    private void addScore(ModeSummary m, Integer score, ScoreField field) {
        if (score == null) {
            return;
        }
        switch (field) {
            case ANSWER_RELEVANCE -> {
                m.setAvgAnswerRelevance((m.getAvgAnswerRelevance() == null ? 0 : m.getAvgAnswerRelevance()) + score);
                m.setAnswerRelevanceSampleCount(m.getAnswerRelevanceSampleCount() + 1);
            }
            case LEGAL_CORRECTNESS -> {
                m.setAvgLegalCorrectness((m.getAvgLegalCorrectness() == null ? 0 : m.getAvgLegalCorrectness()) + score);
                m.setLegalCorrectnessSampleCount(m.getLegalCorrectnessSampleCount() + 1);
            }
            case CITATION_COMPLETENESS -> {
                m.setAvgCitationCompleteness((m.getAvgCitationCompleteness() == null ? 0 : m.getAvgCitationCompleteness()) + score);
                m.setCitationCompletenessSampleCount(m.getCitationCompletenessSampleCount() + 1);
            }
            case HALLUCINATION -> {
                m.setAvgHallucination((m.getAvgHallucination() == null ? 0 : m.getAvgHallucination()) + score);
                m.setHallucinationSampleCount(m.getHallucinationSampleCount() + 1);
            }
        }
    }

    private enum ScoreField { ANSWER_RELEVANCE, LEGAL_CORRECTNESS, CITATION_COMPLETENESS, HALLUCINATION }

    @Override
    public void updateScore(Long resultId, ScoreRequest request, Long userId, String username, String ip) {
        EvaluationResult r = evaluationResultMapper.selectById(resultId);
        if (r == null) {
            throw new BusinessException(404, "评测结果不存在");
        }
        UpdateWrapper<EvaluationResult> wrapper = new UpdateWrapper<EvaluationResult>()
                .eq("id", resultId)
                .set("answer_relevance_score", request.getAnswerRelevanceScore())
                .set("legal_correctness_score", request.getLegalCorrectnessScore())
                .set("citation_completeness_score", request.getCitationCompletenessScore())
                .set("hallucination_score", request.getHallucinationScore())
                .set("manual_comment", request.getManualComment())
                .set("updated_at", LocalDateTime.now());
        evaluationResultMapper.update(null, wrapper);
        safeLog(userId, username, OP_SCORE_UPDATE, String.valueOf(resultId),
                "mode=" + r.getMode() + ",status=" + r.getExecutionStatus(), ip);
    }

    @Override
    public String exportCsv(Long runId, Long userId, String username, String ip) {
        requireRun(runId);
        List<EvaluationResultView> views = listResults(runId);
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // UTF-8 BOM，Excel 中文兼容
        String[] headers = {"结果ID", "题号", "领域", "题目", "方案", "执行状态", "错误码",
                "命中口径", "参考法条(人工标注)", "检索命中", "命中文档ID(Top5)", "响应时间(ms)", "答案", "引用JSON",
                "相关度评分", "法律正确性评分", "引用完整性评分", "幻觉评分(1低5高)", "人工备注"};
        appendCsvRow(sb, List.of(headers));
        for (EvaluationResultView v : views) {
            appendCsvRow(sb, List.of(
                    String.valueOf(v.getId()), v.getQuestionCode(), v.getDomain(), v.getQuestion(),
                    v.getMode(), v.getExecutionStatus(), nvl(v.getErrorCode()),
                    formatMetricVersion(v.getMetricVersion()), formatReferences(v.getReferenceArticlesJson()),
                    v.getRetrievalHit() == null ? "" : String.valueOf(v.getRetrievalHit()),
                    nvl(v.getRetrievedArticleIds()), v.getResponseTimeMs() == null ? "" : String.valueOf(v.getResponseTimeMs()),
                    nvl(v.getAnswer()), nvl(v.getCitationsJson()),
                    score(v.getAnswerRelevanceScore()), score(v.getLegalCorrectnessScore()),
                    score(v.getCitationCompletenessScore()), score(v.getHallucinationScore()),
                    nvl(v.getManualComment())));
        }
        safeLog(userId, username, OP_CSV_EXPORT, String.valueOf(runId),
                "rows=" + views.size(), ip);
        return sb.toString();
    }

    /** 命中口径可读文本：LEGACY_KEYWORD_V1 -> 关键词命中(Legacy)；REFERENCE_ARTICLE_TOP5_V2 -> 参考法条Hit@5 */
    private static String formatMetricVersion(String metricVersion) {
        if (metricVersion == null || metricVersion.isBlank()) {
            return "";
        }
        return METRIC_LEGACY_KEYWORD_V1.equals(metricVersion)
                ? "关键词命中(Legacy)"
                : "参考法条Hit@5";
    }

    /** 参考法条 JSON -> 可读文本：《法律名称》第X条；《法律名称》第Y条 */
    private String formatReferences(String referenceArticlesJson) {
        List<ReferenceKey> refs = parseReferences(referenceArticlesJson);
        if (refs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ReferenceKey r : refs) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append('《').append(r.documentName()).append('》').append(r.articleNumber());
        }
        return sb.toString();
    }

    private static String score(Integer s) {
        return s == null ? "" : String.valueOf(s);
    }

    /** RFC4180 转义 + 公式注入防护（trim 后以 = + - @ 开头 -> 前缀 '） */
    private static void appendCsvRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String raw = cells.get(i) == null ? "" : cells.get(i);
            String trimmed = raw.trim();
            if (!trimmed.isEmpty() && (trimmed.charAt(0) == '='
                    || trimmed.charAt(0) == '+'
                    || trimmed.charAt(0) == '-'
                    || trimmed.charAt(0) == '@')) {
                raw = "'" + raw;
            }
            if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
                sb.append('"').append(raw.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(raw);
            }
        }
        sb.append("\r\n");
    }

    // ---------------------------------------------------------------- outcome carrier

    private static final class ModeOutcome {
        String executionStatus;
        String errorCode;
        String answer;
        Integer retrievalHit;
        String retrievedArticleIds;
        String citationsJson;
        long responseTimeMs;

        ModeOutcome(String executionStatus, String errorCode, String answer,
                    Integer retrievalHit, String retrievedArticleIds, long responseTimeMs,
                    String citationsJson) {
            this.executionStatus = executionStatus;
            this.errorCode = errorCode;
            this.answer = answer;
            this.retrievalHit = retrievalHit;
            this.retrievedArticleIds = retrievedArticleIds;
            this.responseTimeMs = responseTimeMs;
            this.citationsJson = citationsJson;
        }
    }
}
