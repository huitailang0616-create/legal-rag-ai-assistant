package com.ailegal.service.impl;

import com.ailegal.dto.CaseAnalysisResponse;
import com.ailegal.dto.CaseFactExtraction;
import com.ailegal.dto.ConsultCitation;
import com.ailegal.dto.ConsultResponse;
import com.ailegal.dto.EnhancedRetrievalResult;
import com.ailegal.dto.EnhancedRetrieveTestResponse;
import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.dto.QueryUnderstandingResult;
import com.ailegal.dto.RagRetrieveTestResponse;
import com.ailegal.dto.RagTop5Hit;
import com.ailegal.entity.ConsultRuntimeRecord;
import com.ailegal.exception.BusinessException;
import com.ailegal.service.CaseAnalysisService;
import com.ailegal.service.ConsultRuntimeRecordService;
import com.ailegal.service.ConsultService;
import com.ailegal.service.ConsultationHistoryService;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.LlmService;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.QueryUnderstandingService;
import com.ailegal.service.RagService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 4A enhanced consult orchestration:
 *
 *   originalQuestion
 *     -> Query Understanding LLM (20s, json_object)
 *     -> legalIntent=false                          -> NON_LEGAL_INTENT (no RAG, no Answer LLM)
 *     -> legalIntent=true, domain=UNKNOWN           -> UNSUPPORTED_DOMAIN (no Answer LLM)
 *     -> legalIntent=true, 3 supported domains
 *          -> retrievalQuery = canonicalDomainTerm + " " + rewrittenQuery
 *          -> Enhanced RAG (4A-EnhancedCandidateRecall: shared pipeline,
 *             Qdrant candidate limit = 100, same MySQL JOIN + 0.70/0.20/0.10
 *             rerank + Top-5 + 0.65 gate; Basic retrieveTest stays limit=10)
 *          -> gate reject                           -> KNOWLEDGE_GAP (no Answer LLM)
 *          -> gate PASS: Top-5 MySQL evidence + originalQuestion -> Answer LLM
 *             -> answer + backend citations (evidence order)
 *
 * Guarantees:
 *  - The shared retrieval pipeline has no duplicated embedding/ranking/gate logic.
 *    Basic ragService.retrieveTest (limit=10) is NOT used by this consult path.
 *  - The Answer LLM only ever sees Top-5 MySQL legal text + originalQuestion.
 *    It never sees rewrittenQuery / keyFacts / JSON / scores / debug info.
 *  - retrievalQuery is retrieval-only: never shown to users, never sent to the
 *    Answer LLM, never stored in operation_log.
 *  - Demo-Reliability-1: Query Understanding technical failure (429 / provider 5xx /
 *    timeout — every BusinessException with a "问题理解失败" message) falls back to the
 *    locked Basic RAG pipeline (retrieveTest, limit=10) instead of failing the whole
 *    consult. NON_LEGAL_INTENT / UNSUPPORTED_DOMAIN are normal QU outcomes and never
 *    fall back. Non-QU failures still surface as HTTP 500.
 *  - Domain disagreement between understanding and Basic detector is logged as
 *    DOMAIN_DISAGREEMENT and never silently overridden.
 */
@Service
public class ConsultServiceImpl implements ConsultService {

    private static final Logger log = LoggerFactory.getLogger(ConsultServiceImpl.class);

    private static final String CONSULT_OP = "CONSULT_RAG";

    private static final String INSUFFICIENT_ANSWER =
            "当前知识库未检索到足够直接的法律依据，暂时无法基于可靠依据给出具体法律结论。"
                    + "建议补充更具体的事实信息，或咨询专业法律人士。";

    /** Evidence snippet length for citations: keep it modest (150-300 chars). */
    private static final int SNIPPET_MAX = 200;

    /**
     * Canonical domain terms — verified against the current 3A Basic domain dictionary
     * (RagServiceImpl.DOMAIN_KEYWORDS) and KeywordConceptGroups questionAliases:
     * each term IS a substring in the Basic dictionary of its domain and does NOT
     * trigger any KeywordConceptGroups concept. Prepending it lets the 3A domain
     * detector naturally confirm the domain the Query Understanding module chose.
     */
    private static final Map<String, String> CANONICAL_DOMAIN_TERM = Map.of(
            "LABOR_DISPUTE", "劳动争议",
            "HOUSE_RENTAL", "房屋租赁",
            "CONSUMER_RIGHTS", "消费者权益"
    );

    private final RagService ragService;
    private final LegalKnowledgeService legalKnowledgeService;
    private final LlmService llmService;
    private final OperationLogService operationLogService;
    private final QueryUnderstandingService queryUnderstandingService;
    private final ConsultationHistoryService consultationHistoryService;
    private final ConsultRuntimeRecordService consultRuntimeRecordService;
    private final CaseAnalysisService caseAnalysisService;
    private final ObjectMapper objectMapper;
    private final String modelName;

    public ConsultServiceImpl(RagService ragService,
                              LegalKnowledgeService legalKnowledgeService,
                              LlmService llmService,
                              OperationLogService operationLogService,
                              QueryUnderstandingService queryUnderstandingService,
                              ConsultationHistoryService consultationHistoryService,
                              ConsultRuntimeRecordService consultRuntimeRecordService,
                              CaseAnalysisService caseAnalysisService,
                              ObjectMapper objectMapper,
                              @Value("${llm.model}") String modelName) {
        this.ragService = ragService;
        this.legalKnowledgeService = legalKnowledgeService;
        this.llmService = llmService;
        this.operationLogService = operationLogService;
        this.queryUnderstandingService = queryUnderstandingService;
        this.consultationHistoryService = consultationHistoryService;
        this.consultRuntimeRecordService = consultRuntimeRecordService;
        this.caseAnalysisService = caseAnalysisService;
        this.objectMapper = objectMapper;
        this.modelName = modelName;
    }

    @Override
    public ConsultResponse consult(String question, Long caseAnalysisId, Long userId, String username, String ip) {
        long t0 = System.currentTimeMillis();
        // Final-Requirement-Gaps-B: telemetry record for ADMIN run management.
        // Populated along the pipeline; one terminal row (SUCCESS/REJECTED/FAILED) per consult.
        ConsultRuntimeRecord record = new ConsultRuntimeRecord();
        record.setUserId(userId);
        record.setUsername(username);
        record.setQuestion(question);
        try {
            // Final-Requirement-Gaps-G: optional linked confirmed case (request-lifecycle only).
            // Loaded BEFORE the pipeline so ownership / confirmed failures surface as
            // stable 404 / 400 CASE_EXTRACTION_NOT_CONFIRMED instead of being masked.
            String caseContext = null;
            if (caseAnalysisId != null) {
                caseContext = loadConfirmedCaseContext(caseAnalysisId, userId);
            }
            // contextualQuestion: only the QU input text changes; everything else keeps
            // using the raw user question. No caseAnalysisId -> identical to legacy.
            String contextualQuestion = caseContext == null
                    ? question
                    : caseContext + "\n【用户当前咨询问题】\n" + question;
            EnhancedRetrievalResult er;
            try {
                er = runEnhancedRetrieval(contextualQuestion, userId, username, ip, record);
            } catch (BusinessException e) {
                if (!isQueryUnderstandingFailure(e)) {
                    // non-QU failure (RAG stage etc.): FAILED record before rethrow.
                    // Runtime-Hotfix-1-Fix1: domain is kept as already recognised by QU
                    // (runEnhancedRetrieval writes it right after a successful QU call);
                    // only a failure before any domain determination falls back to UNKNOWN.
                    record.setRetrievalMode("ENHANCED_RAG");
                    record.setErrorCode(mapError(e));
                    saveRuntime(record, "FAILED", t0);
                    throw e;
                }
                // Demo-Reliability-1: QU transient provider failure (429 / 5xx / timeout)
                // degrades to the locked Basic RAG pipeline instead of failing the consult.
                logFailure(userId, username, "UNKNOWN",
                        "失败:queryUnderstanding:" + shortMessage(e) + ",fallback=BASIC_RAG", ip);
                // Runtime-Hotfix-1: record the actual execution path BEFORE the attempt, so a
                // Basic fallback that also fails still persists retrieval_mode=BASIC_FALLBACK
                // (the attempt really ran) instead of a misleading NO_RETRIEVAL.
                record.setRetrievalMode("BASIC_FALLBACK");
                er = runBasicFallback(question, userId, username, ip);
                record.setFallbackReason(buildFallbackReason(e));
            }
            record.setRetrievalMode(retrievalModeOf(er));
            record.setDomain(er.getBasicDetectedDomain());

            // ---- reject branches: no Answer LLM, empty citations ----
            if (!er.isRelevancePassed()) {
                ConsultResponse resp = new ConsultResponse(reasonAnswer(er.getEnhancedReason()), er.getBasicDetectedDomain(),
                        true, er.getEnhancedReason(), List.of());
                consultationHistoryService.save(userId, question, resp);
                logResult(userId, username, er.getBasicDetectedDomain(), logSuffix(er), ip);
                perfLog(er.getQueryUnderstandingMs(), er.getRagMs(), 0L, er.getEnhancedReason());
                record.setRelevanceReason(er.getEnhancedReason());
                record.setRetrievedArticleIds(serializeTop5Ids(er.getTop5()));
                record.setCitationsJson("[]");
                record.setAnswer(resp.getAnswer());
                saveRuntime(record, "REJECTED", t0);
                return resp;
            }

            // ---- PASS: Top-5 authoritative MySQL data (never Qdrant payload) ----
            List<Long> top5Ids = er.getTop5().stream()
                    .map(RagTop5Hit::getArticleId)
                    .toList();
            List<LegalArticleDocument> ordered = loadOrdered(top5Ids);
            if (ordered.isEmpty()) {
                ConsultResponse resp = new ConsultResponse(INSUFFICIENT_ANSWER, er.getBasicDetectedDomain(),
                        true, "KNOWLEDGE_GAP", List.of());
                consultationHistoryService.save(userId, question, resp);
                logResult(userId, username, er.getBasicDetectedDomain(),
                        "understanding=true,legalIntent=true,domain=" + er.getSupportedDomain()
                                + ",rewrite=true,gate=KNOWLEDGE_GAP,basicReason=NO_RESULT", ip);
                perfLog(er.getQueryUnderstandingMs(), er.getRagMs(), 0L, "KNOWLEDGE_GAP");
                record.setRelevanceReason("KNOWLEDGE_GAP");
                record.setRetrievedArticleIds(serializeTop5Ids(er.getTop5()));
                record.setCitationsJson("[]");
                record.setAnswer(resp.getAnswer());
                saveRuntime(record, "REJECTED", t0);
                return resp;
            }

            // ---- Answer LLM grounded strictly in Top-5 MySQL evidence + originalQuestion ----
            // Linked case: the user-confirmed case context is appended to the user prompt and a
            // grounding rule is added via the request-level system prompt (LlmService global
            // prompt untouched); legal basis & citations still come only from the Top-5 evidence.
            long t4 = System.currentTimeMillis();
            String evidence = buildEvidenceBlock(ordered);
            String answer;
            try {
                answer = llmService.chat(buildSystemPrompt(caseContext != null),
                        buildUserPrompt(question, evidence, caseContext));
            } catch (BusinessException e) {
                logFailure(userId, username, er.getSupportedDomain(), e.getMessage(), ip);
                record.setErrorCode(mapError(e));
                saveRuntime(record, "FAILED", t0);
                throw e;
            }
            long t5 = System.currentTimeMillis();

            List<ConsultCitation> citations = buildCitations(ordered);
            ConsultResponse resp = new ConsultResponse(answer, er.getBasicDetectedDomain(), false, "PASS", citations);
            consultationHistoryService.save(userId, question, resp);
            logResult(userId, username, er.getBasicDetectedDomain(),
                    logSuffix(er) + ",citations=" + citations.size() + ",model=" + modelName, ip);
            perfLog(er.getQueryUnderstandingMs(), er.getRagMs(), t5 - t4, "PASS");
            record.setRelevanceReason("PASS");
            record.setRetrievedArticleIds(serializeTop5Ids(er.getTop5()));
            record.setCitationsJson(serializeCitations(citations));
            record.setAnswer(answer);
            saveRuntime(record, "SUCCESS", t0);
            return resp;
        } catch (BusinessException e) {
            // history save failure (HISTORY_SAVE_ERROR) etc.: FAILED record if not already written
            if (record.getExecutionStatus() == null) {
                record.setErrorCode(mapError(e));
                saveRuntime(record, "FAILED", t0);
            }
            throw e;
        } catch (Exception e) {
            if (record.getExecutionStatus() == null) {
                record.setErrorCode("INTERNAL_ERROR");
                saveRuntime(record, "FAILED", t0);
            }
            throw e;
        }
    }

    @Override
    public EnhancedRetrieveTestResponse enhancedRetrieveTest(String question, Long userId, String username, String ip) {
        long t0 = System.currentTimeMillis();
        EnhancedRetrievalResult er = runEnhancedRetrieval(question, userId, username, ip, null);
        er.setTotalMs(System.currentTimeMillis() - t0);

        EnhancedRetrieveTestResponse resp = new EnhancedRetrieveTestResponse();
        resp.setLegalIntent(er.isLegalIntent());
        resp.setSupportedDomain(er.getSupportedDomain());
        resp.setRewrittenQuery(er.getRewrittenQuery());
        resp.setKeyFacts(er.getKeyFacts());
        resp.setRetrievalQuery(er.getRetrievalQuery());
        resp.setBasicDetectedDomain(er.getBasicDetectedDomain());
        resp.setQueryConcepts(er.getQueryConcepts());
        resp.setTop5(er.getTop5());
        resp.setTop1(er.getTop5() == null || er.getTop5().isEmpty() ? null : er.getTop5().get(0));
        resp.setRelevancePassed(er.isRelevancePassed());
        resp.setEnhancedReason(er.getEnhancedReason());
        resp.setBasicReason(er.getBasicReason());
        resp.setQueryUnderstandingMs(er.getQueryUnderstandingMs());
        resp.setRagMs(er.getRagMs());
        resp.setTotalMs(er.getTotalMs());
        return resp;
    }

    /**
     * Shared stage 1+2 of the enhanced pipeline: Query Understanding -> Basic RAG.
     * Shared by /api/consult and the enhanced-retrieve-test technical API. Never
     * calls the Answer LLM. Query Understanding failures propagate as HTTP 500.
     *
     * quInput 是 Query Understanding 的输入文本：普通咨询 = 用户原始问题；
     * 关联案件咨询 = 请求级 contextualQuestion（案件背景 + 用户问题）。
     * 检索侧仍只使用 QU 改写后的 rewrittenQuery（案件语境经改写注入），
     * 不改变 Basic / Enhanced 算法、TopK、权重、gate、KeywordConceptGroups、Embedding、Qdrant。
     */
    private EnhancedRetrievalResult runEnhancedRetrieval(String quInput, Long userId, String username, String ip,
                                                          ConsultRuntimeRecord record) {
        long t0 = System.currentTimeMillis();

        // ---- Stage 1: Query Understanding (understand only, never answer) ----
        QueryUnderstandingResult qu;
        try {
            qu = queryUnderstandingService.understand(quInput);
        } catch (BusinessException e) {
            logFailure(userId, username, "UNKNOWN", "失败:queryUnderstanding:" + shortMessage(e), ip);
            throw e;
        }
        long t1 = System.currentTimeMillis();
        // Runtime-Hotfix-1-Fix1: persist the domain as soon as QU has recognised it, so a
        // later RAG/Qdrant/Answer failure keeps the real domain instead of degrading to
        // UNKNOWN. UNKNOWN is only used when the failure happens before domain determination.
        if (record != null) {
            record.setDomain(qu.getSupportedDomain());
        }

        EnhancedRetrievalResult er = new EnhancedRetrievalResult();
        er.setLegalIntent(qu.isLegalIntent());
        er.setSupportedDomain(qu.getSupportedDomain());
        er.setRewrittenQuery(qu.getRewrittenQuery());
        er.setKeyFacts(qu.getKeyFacts());
        er.setQueryUnderstandingMs(t1 - t0);

        // legalIntent=false: reject, no RAG, no Answer LLM
        if (!qu.isLegalIntent()) {
            er.setBasicDetectedDomain("UNKNOWN");
            er.setRelevancePassed(false);
            er.setEnhancedReason("NON_LEGAL_INTENT");
            er.setTotalMs(System.currentTimeMillis() - t0);
            return er;
        }

        // legal question but outside the three supported domains: reject, no Answer LLM
        if (QueryUnderstandingResult.UNKNOWN.equals(qu.getSupportedDomain())) {
            er.setBasicDetectedDomain("UNKNOWN");
            er.setRelevancePassed(false);
            er.setEnhancedReason("UNSUPPORTED_DOMAIN");
            er.setTotalMs(System.currentTimeMillis() - t0);
            return er;
        }

        // ---- Stage 2: retrievalQuery -> Enhanced RAG (4A-EnhancedCandidateRecall) ----
        // Enhanced uses ragService.retrieveEnhanced (Qdrant candidate limit = 100).
        // Basic ragService.retrieveTest (limit = 10) stays strictly unchanged for
        // /api/admin/rag/retrieve-test and the 3A calibration.
        String retrievalQuery = CANONICAL_DOMAIN_TERM.get(qu.getSupportedDomain())
                + " " + qu.getRewrittenQuery();
        er.setRetrievalQuery(retrievalQuery);

        long t2 = System.currentTimeMillis();
        RagRetrieveTestResponse retrieval;
        try {
            retrieval = ragService.retrieveEnhanced(retrievalQuery, userId, username, ip);
        } catch (BusinessException e) {
            logFailure(userId, username, qu.getSupportedDomain(), "失败:rag:" + shortMessage(e), ip);
            throw e;
        }
        long t3 = System.currentTimeMillis();
        er.setRagMs(t3 - t2);
        er.setBasicDetectedDomain(retrieval.getDetectedDomain());
        er.setQueryConcepts(retrieval.getQueryConcepts());
        er.setTop5(retrieval.getTop5());
        er.setBasicReason(retrieval.getRelevanceReason());

        // Domain consistency: log real disagreement, never silently override results
        if (!qu.getSupportedDomain().equals(retrieval.getDetectedDomain())) {
            logResult(userId, username, qu.getSupportedDomain(),
                    "DOMAIN_DISAGREEMENT:understanding=" + qu.getSupportedDomain()
                            + ",basic=" + retrieval.getDetectedDomain(), ip);
        }

        if (!retrieval.isRelevancePassed()) {
            // understood correctly, but the current formal KB lacks direct evidence
            er.setRelevancePassed(false);
            er.setEnhancedReason("KNOWLEDGE_GAP");
            er.setTotalMs(System.currentTimeMillis() - t0);
            return er;
        }

        er.setRelevancePassed(true);
        er.setEnhancedReason("PASS");
        er.setTotalMs(System.currentTimeMillis() - t0);
        return er;
    }

    // ---- Demo-Reliability-1: QU technical failure -> Basic RAG fallback ----

    /**
     * Only technical Query Understanding failures degrade to Basic RAG: every QU
     * provider failure (429 / provider 5xx / connect+read timeout) is thrown as a
     * BusinessException whose message starts with "问题理解失败" — the same substring
     * EvaluationExecutionServiceImpl.mapError uses. NON_LEGAL_INTENT and
     * UNSUPPORTED_DOMAIN are normal outcomes of a successful QU call, never thrown
     * here, so they can never trigger this fallback.
     */
    private boolean isQueryUnderstandingFailure(BusinessException e) {
        String msg = e.getMessage();
        return msg != null && msg.startsWith("问题理解失败");
    }

    /**
     * Fallback retrieval: the original user question goes through the existing
     * locked Basic RAG public entry ragService.retrieveTest (candidate limit = 10,
     * same pipeline, same 0.70/0.20/0.10 weights, same Top-5, same 0.65 gate — no
     * copied algorithm). The result is mapped into EnhancedRetrievalResult so the
     * shared PASS / gate-reject branches of consult() apply unchanged; if the Basic
     * gate rejects, the normal KNOWLEDGE_GAP safe-rejection answer is returned.
     */
    private EnhancedRetrievalResult runBasicFallback(String question, Long userId, String username, String ip) {
        long t0 = System.currentTimeMillis();
        RagRetrieveTestResponse retrieval = ragService.retrieveTest(question, userId, username, ip);
        long t1 = System.currentTimeMillis();

        EnhancedRetrievalResult er = new EnhancedRetrievalResult();
        er.setLegalIntent(true);
        er.setSupportedDomain(retrieval.getDetectedDomain());
        er.setRewrittenQuery("");
        er.setKeyFacts(List.of());
        er.setBasicDetectedDomain(retrieval.getDetectedDomain());
        er.setQueryConcepts(retrieval.getQueryConcepts());
        er.setTop5(retrieval.getTop5());
        er.setBasicReason(retrieval.getRelevanceReason());
        er.setRelevancePassed(retrieval.isRelevancePassed());
        er.setEnhancedReason(retrieval.isRelevancePassed() ? "PASS" : "KNOWLEDGE_GAP");
        er.setFallbackToBasic(true);
        er.setQueryUnderstandingMs(0L);
        er.setRagMs(t1 - t0);
        er.setTotalMs(System.currentTimeMillis() - t0);
        return er;
    }

    // ---- operation log (short, safe: never question/prompt/answer/rewrite/secret) ----

    private static String logSuffix(EnhancedRetrievalResult er) {
        String s = "understanding=true,legalIntent=" + er.isLegalIntent()
                + ",domain=" + er.getSupportedDomain() + ",rewrite=true,gate=" + er.getEnhancedReason();
        if ("KNOWLEDGE_GAP".equals(er.getEnhancedReason())) {
            s += ",basicReason=" + er.getBasicReason();
        }
        if (er.isFallbackToBasic()) {
            s += ",fallback=BASIC_RAG_QU_FAILED";
        }
        return s;
    }

    private static String reasonAnswer(String reason) {
        if ("NON_LEGAL_INTENT".equals(reason)) {
            return "当前问题似乎不是法律咨询问题。本系统主要提供劳动争议、房屋租赁和消费者权益领域的法律信息咨询。";
        }
        if ("UNSUPPORTED_DOMAIN".equals(reason)) {
            return "该问题属于法律咨询，但目前超出本系统支持的劳动争议、房屋租赁和消费者权益范围。建议咨询相关领域的专业法律人士。";
        }
        return INSUFFICIENT_ANSWER;
    }

    private void perfLog(long quMs, long ragMs, long answerMs, String gate) {
        log.info("ENHANCED_CONSULT_PERF queryUnderstandingMs={} ragMs={} answerLlmMs={} totalMs={} gate={}",
                quMs, ragMs, answerMs, quMs + ragMs + answerMs, gate);
    }

    // ---- prompts (answer grounded strictly in the provided Top-5 evidence) ----

    private static final String SYSTEM_PROMPT =
            "你是中国法律信息咨询辅助系统，不是律师。\n"
                    + "\n"
                    + "回答规则：\n"
                    + "1. 只能依据本次对话提供的【法律依据】回答，不得自行补充依据之外的法律名称、法条编号、判例、司法解释、数字或法律结论。\n"
                    + "2. 如果现有依据无法支持问题中的某个具体结论，明确说明“现有依据不足以确认该点”，不要猜测。\n"
                    + "3. 不得声称“保证胜诉”“法院一定支持”“你一定能获得赔偿”等确定性结果。\n"
                    + "4. 回答使用中文，清楚易懂，普通用户能读懂。\n"
                    + "5. 回答结构：先给结论性说明，再说明法律依据，最后给实际处理建议。\n"
                    + "6. 建议只允许一般性程序建议（如保留证据、协商、咨询相关主管部门或专业人士），不得虚构具体电话、机构地址或程序期限。\n"
                    + "7. 结尾必须包含免责声明：“以上内容仅供法律信息参考，不构成正式法律意见。”\n"
                    + "8. 你拥有的模型训练知识不能作为本次回答依据。即使你知道某条中国法律规则，只要该规则没有出现在本次提供的【法律依据】中，就不得写入回答。\n"
                    + "9. 必须逐个判断用户问题中的子问题。如果本次提供的依据只能支持其中一部分，只回答有直接依据的部分；对于没有直接依据的部分，明确写“根据当前检索到的法律依据，暂不足以直接判断这一点。”不得用模型自身知识补齐。\n"
                    + "10. 不得因为某规则“常识上正确”就加入回答。真实性优先于完整性。\n"
                    + "11. 对于赔偿金额、违约金比例、违约金调整、具体期限、法定倍数、法律责任条件等具体规则，必须能够在本次【法律依据】文本中找到直接支持，否则不得给出该规则。\n"
                    + "12. 不要告诉用户“我的知识库没有……”“Top-5……”“RAG……”。保持正常用户语言，可以表达为：“根据当前检索到的法律依据，尚不足以确定……”";

    private static String buildEvidenceBlock(List<LegalArticleDocument> ordered) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (LegalArticleDocument a : ordered) {
            sb.append("【依据").append(idx).append("】\n");
            sb.append("法规：").append(a.getDocumentName()).append("\n");
            sb.append("条款：").append(a.getArticleNumber()).append("\n");
            sb.append("正文：").append(a.getContent()).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    private static String buildUserPrompt(String question, String evidence) {
        return buildUserPrompt(question, evidence, null);
    }

    /**
     * 关联案件版 user prompt：证据块 +（可选）【案件背景】块 + 用户原始问题。
     * caseContext 为 null 时输出与原签名逐字节一致（普通咨询零回归）。
     */
    private static String buildUserPrompt(String question, String evidence, String caseContext) {
        StringBuilder sb = new StringBuilder(evidence);
        if (caseContext != null && !caseContext.isBlank()) {
            sb.append("\n").append(caseContext).append("\n");
        }
        sb.append("【用户问题】\n").append(question).append("\n\n")
                .append("回答时可以引用 [依据1]、[依据2] 这样的编号。");
        return sb.toString();
    }

    /**
     * 关联案件咨询的请求级 grounding 规则：案件背景是用户已确认陈述的事实背景，
     * 不是法律依据；法律规则、法条、权利、责任、赔偿、期限等内容仍只能以
     * 【法律依据】为准。LlmService 全局 system prompt 不修改。
     */
    private static final String CASE_LINKED_GROUNDING =
            "\n13. 本次对话中出现的【案件背景】是用户已确认陈述的案件事实背景，仅用于理解用户处境与问题语境，"
                    + "不是法律依据。回答涉及法律规则、法条、权利、责任、赔偿、期限等法律内容时，"
                    + "必须严格依据本次提供的【法律依据】，不得把案件背景中的任何陈述当作法律依据引用或据此推导法律结论。";

    private static String buildSystemPrompt(boolean linkedCase) {
        return linkedCase ? SYSTEM_PROMPT + CASE_LINKED_GROUNDING : SYSTEM_PROMPT;
    }

    // ---- Final-Requirement-Gaps-G: confirmed case context (request-lifecycle only) ----

    private static final Map<String, String> DOMAIN_LABEL = Map.of(
            "LABOR_DISPUTE", "劳动争议",
            "HOUSE_RENTAL", "房屋租赁",
            "CONSUMER_RIGHTS", "消费者权益");

    /**
     * 合同情况 → 中文说明。UNKNOWN / null 一律表述为"未提供或不清楚"，
     * 不得推断为任何事实（案件上下文不能自行产生事实主张）。
     */
    private static String contractStatusLabel(String s) {
        if ("SIGNED".equals(s)) return "已签订书面合同";
        if ("ORAL".equals(s)) return "口头约定（未签订书面合同）";
        if ("UNSIGNED".equals(s)) return "未签订合同";
        return "未提供或不清楚";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * 读取本人已确认案件（Final-Requirement-Gaps-G 使用条件）：
     * 1) 案件属于当前 USER（未删除）——由 CaseAnalysisService.get 复用 getOwn 联合定位，
     *    不存在 / 非本人统一 404（既有 ownership 语义，不泄露存在性）；
     * 2) extraction_confirmed=1；
     * 3) extraction_json 存在且能通过现有严格校验解析（解析失败按 null，安全拒绝）。
     * 不满足 2/3 时返回稳定业务错误 400 + CASE_EXTRACTION_NOT_CONFIRMED。
     * 上下文只在本次请求生命周期内存在，不落任何持久化。
     */
    private String loadConfirmedCaseContext(Long caseAnalysisId, Long userId) {
        CaseAnalysisResponse ca = caseAnalysisService.get(caseAnalysisId, userId);
        if (ca.getExtractionConfirmed() == null || ca.getExtractionConfirmed() != 1 || ca.getExtraction() == null) {
            throw new BusinessException(400, "CASE_EXTRACTION_NOT_CONFIRMED");
        }
        return buildCaseContext(ca);
    }

    /**
     * 把已确认案件的 extraction（已人工确认的 8 项冻结字段）+ 表级 amount/contractStatus
     * 拼装为【案件背景】纯文本。missingInformation 独立成块并显式标注为信息缺口，
     * 不是既定事实。该文本只进入 QU 输入与 Answer user prompt，不进历史 / 遥测 / URL。
     */
    private String buildCaseContext(CaseAnalysisResponse ca) {
        CaseFactExtraction ext = ca.getExtraction();
        StringBuilder sb = new StringBuilder();
        sb.append("【案件背景】（以下为本人在案件中已人工确认的信息，仅供理解语境，不是法律依据）：\n");
        sb.append("【案件领域】").append(DOMAIN_LABEL.getOrDefault(ca.getDomain(), nz(ca.getDomain()))).append("\n");
        sb.append("【案件摘要】").append(nz(ext.getCaseSummary())).append("\n");
        sb.append("【争议焦点】").append(nz(ext.getDisputeFocus())).append("\n");
        if (ext.getClaims() != null && !ext.getClaims().isEmpty()) {
            sb.append("【核心诉求】").append(String.join("；", ext.getClaims())).append("\n");
        }
        if (ext.getKeyDates() != null && !ext.getKeyDates().isEmpty()) {
            sb.append("【关键时间】");
            for (int i = 0; i < ext.getKeyDates().size(); i++) {
                CaseFactExtraction.KeyDateItem k = ext.getKeyDates().get(i);
                if (i > 0) sb.append("；");
                sb.append(nz(k.getEvent())).append("：").append(nz(k.getDateText()));
            }
            sb.append("\n");
        }
        if (ext.getEvidenceItems() != null && !ext.getEvidenceItems().isEmpty()) {
            sb.append("【已有证据】");
            for (int i = 0; i < ext.getEvidenceItems().size(); i++) {
                CaseFactExtraction.EvidenceItem e = ext.getEvidenceItems().get(i);
                if (i > 0) sb.append("；");
                sb.append(nz(e.getName()));
                if (e.getDescription() != null && !e.getDescription().isBlank()) {
                    sb.append("（").append(e.getDescription()).append("）");
                }
            }
            sb.append("\n");
        }
        if (ext.getCounterparty() != null && !nz(ext.getCounterparty().getDescription()).isBlank()) {
            sb.append("【对方主体】").append(ext.getCounterparty().getDescription()).append("\n");
        }
        if (ext.getLocation() != null && !nz(ext.getLocation().getDescription()).isBlank()) {
            sb.append("【地点】").append(ext.getLocation().getDescription()).append("\n");
        }
        sb.append("【涉及金额】").append(ca.getAmount() == null ? "未提供" : ca.getAmount().toPlainString() + " 元").append("\n");
        sb.append("【合同情况】").append(contractStatusLabel(ca.getContractStatus())).append("\n");
        if (ext.getMissingInformation() != null) {
            List<String> miss = ext.getMissingInformation().stream()
                    .filter(m -> m != null && !m.isBlank()).toList();
            if (!miss.isEmpty()) {
                sb.append("【待补充信息】（以下事项在本案中尚未提供或尚未明确，是信息缺口，不是既定事实）：\n")
                        .append(String.join("；", miss)).append("\n");
            }
        }
        return sb.toString();
    }

    // ---- backend-generated citations (evidence order 1..5, never parsed from the answer) ----

    private static List<ConsultCitation> buildCitations(List<LegalArticleDocument> ordered) {
        List<ConsultCitation> citations = new ArrayList<>();
        for (LegalArticleDocument a : ordered) {
            String content = a.getContent() == null ? "" : a.getContent();
            String snippet = content.length() <= SNIPPET_MAX
                    ? content
                    : content.substring(0, SNIPPET_MAX);
            citations.add(new ConsultCitation(
                    a.getArticleId(), a.getDocumentId(), a.getDocumentName(),
                    a.getArticleNumber(), a.getSource(), snippet));
        }
        return citations;
    }

    // ---- ordered Top-5 MySQL fetch (keeps exact ranking order) ----

    private List<LegalArticleDocument> loadOrdered(List<Long> top5Ids) {
        List<LegalArticleDocument> articles = legalKnowledgeService.selectValidArticlesWithDocuments(top5Ids);
        Map<Long, LegalArticleDocument> byId = new HashMap<>();
        for (LegalArticleDocument a : articles) {
            byId.put(a.getArticleId(), a);
        }
        List<LegalArticleDocument> ordered = new ArrayList<>();
        for (Long id : top5Ids) {
            LegalArticleDocument a = byId.get(id);
            if (a != null) {
                ordered.add(a);
            }
        }
        return ordered;
    }

    // ---- Final-Requirement-Gaps-B: consult runtime record (telemetry, never breaks business) ----

    /**
     * Terminal write: sets status/errorCode/responseTimeMs, then saveQuietly (swallow + warn).
     * Runtime-Hotfix-1: null-only normalisation right before insert, so technical FAILED rows
     * always satisfy the NOT NULL columns of consult_runtime_record (citations_json, domain,
     * retrieval_mode) with schema-conformant values ("[]" per column comment "拒绝/失败为空 []",
     * "UNKNOWN" / "NO_RETRIEVAL" per CHECK enum) instead of a silent lost row. Null-only: never
     * overwrites real values; SUCCESS/REJECTED rows are untouched (their fields are already set).
     */
    private void saveRuntime(ConsultRuntimeRecord record, String status, long t0) {
        if (record.getCitationsJson() == null) {
            record.setCitationsJson("[]");
        }
        if (record.getRetrievedArticleIds() == null) {
            record.setRetrievedArticleIds("[]");
        }
        if (record.getDomain() == null) {
            record.setDomain("UNKNOWN");
        }
        if (record.getRetrievalMode() == null) {
            record.setRetrievalMode("NO_RETRIEVAL");
        }
        record.setExecutionStatus(status);
        record.setResponseTimeMs((int) (System.currentTimeMillis() - t0));
        consultRuntimeRecordService.saveQuietly(record);
    }

    /**
     * Actual execution path: BASIC_FALLBACK whenever the QU technical failure degraded
     * to Basic RAG; NO_RETRIEVAL when rejected by QU before any RAG ran; ENHANCED_RAG otherwise.
     */
    private static String retrievalModeOf(EnhancedRetrievalResult er) {
        if (er.isFallbackToBasic()) {
            return "BASIC_FALLBACK";
        }
        String reason = er.getEnhancedReason();
        if ("NON_LEGAL_INTENT".equals(reason) || "UNSUPPORTED_DOMAIN".equals(reason)) {
            return "NO_RETRIEVAL";
        }
        return "ENHANCED_RAG";
    }

    /** Short fallback trigger reason (e.g. QU_FAILURE(429) / QU_FAILURE:TIMEOUT). */
    private static String buildFallbackReason(BusinessException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("(429)")) {
            return "QU_FAILURE(429)";
        }
        if (msg.contains("超时")) {
            return "QU_FAILURE:TIMEOUT";
        }
        return "QU_FAILURE";
    }

    /** Stable short error code, aligned with EvaluationExecutionServiceImpl.mapError. */
    private static String mapError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "INTERNAL_ERROR";
        }
        if (msg.contains("咨询记录保存失败")) {
            return "HISTORY_SAVE_ERROR";
        }
        if (msg.contains("CASE_EXTRACTION_NOT_CONFIRMED")) {
            return "CASE_NOT_CONFIRMED";
        }
        if (msg.contains("案件不存在")) {
            // Runtime-Hotfix-1-Fix1: linked case missing / not owned by the user keeps the
            // HTTP 404 ownership semantics, but must not be misclassified as PROVIDER_ERROR.
            return "CASE_NOT_FOUND";
        }
        if (msg.contains("问题理解失败")) {
            return "QU_ERROR";
        }
        if (msg.contains("检索失败:Embedding")) {
            return "EMBEDDING_ERROR";
        }
        if (msg.contains("检索失败:Qdrant")) {
            return "QDRANT_ERROR";
        }
        if (msg.contains("检索失败:MySQL")) {
            return "RETRIEVAL_DB_ERROR";
        }
        if (msg.contains("(429)")) {
            return "RATE_LIMIT";
        }
        if (msg.contains("超时")) {
            return "TIMEOUT";
        }
        if (e instanceof BusinessException) {
            return "PROVIDER_ERROR";
        }
        return "INTERNAL_ERROR";
    }

    /** Top-5 article ids in evidence-block order (ranking order), or null when no retrieval ran. */
    private String serializeTop5Ids(List<RagTop5Hit> top5) {
        if (top5 == null || top5.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(top5.stream().map(RagTop5Hit::getArticleId).toList());
        } catch (JsonProcessingException e) {
            log.warn("runtime top5 ids serialize failed", e);
            return null;
        }
    }

    /** Backend-generated citations snapshot (ConsultCitation six fields), never raw LLM output. */
    private String serializeCitations(List<ConsultCitation> citations) {
        try {
            return objectMapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (JsonProcessingException e) {
            log.warn("runtime citations serialize failed", e);
            return "[]";
        }
    }

    // ---- operation log helpers ----

    private void logResult(Long userId, String username, String domain, String result, String ip) {
        try {
            operationLogService.log(userId, username, CONSULT_OP, domain, result, ip);
        } catch (Exception logEx) {
            // logging must never break the consult
        }
    }

    private void logFailure(Long userId, String username, String domain, String result, String ip) {
        try {
            operationLogService.logFailure(userId, username, CONSULT_OP, domain, result, ip);
        } catch (Exception logEx) {
            // logging must never break the consult
        }
    }

    private String shortMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }
}
