package com.ailegal.service.impl;

import com.ailegal.dto.KeywordConceptGroups;
import com.ailegal.dto.KeywordConceptGroups.ConceptGroup;
import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.dto.QdrantSearchResult;
import com.ailegal.dto.RagRetrieveTestResponse;
import com.ailegal.dto.RagTop10Hit;
import com.ailegal.dto.RagTop5Hit;
import com.ailegal.exception.BusinessException;
import com.ailegal.service.EmbeddingService;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.QdrantService;
import com.ailegal.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Basic RAG retrieval pipeline (3A) + Enhanced candidate recall (4A-EnhancedCandidateRecall).
 *
 * Fixed weights: vector 0.70, keyword 0.20, domain 0.10.
 * vectorScoreNormalized = clamp((cosine + 1.0) / 2.0, 0.0, 1.0) — no min-max.
 * finalScore = 0.70*VSN + 0.20*keywordScore + 0.10*domainScore.
 * Sort by finalScore desc, tie by raw vectorScore desc, take Top-5.
 *
 * Basic and Enhanced share ONE pipeline (retrieveInternal); the ONLY difference
 * is the first-stage Qdrant candidate limit: Basic = 10 (3A director-accepted),
 * Enhanced = 100 (4A: direct legal basis may sit deeper in the vector ranking
 * after Query Rewrite — 第五百八十五条 raw rank observed 43~85).
 *
 * Failure semantics:
 *  - Embedding failure -> retrieval fails, Qdrant is never called, no fake Top-10.
 *  - Qdrant failure    -> retrieval fails, no MySQL LIKE fallback.
 *  - MySQL failure     -> Qdrant payload is never returned as authoritative legal text.
 */
@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private static final String UNKNOWN_DOMAIN = "UNKNOWN";

    /** Fixed domain dictionary — linked ONLY to domain scoring. */
    private static final Map<String, List<String>> DOMAIN_KEYWORDS = Map.of(
            // 3A-GateLock: added "试用期" — real calibration showed "试用期最长可以约定多久？"
            // recalled 劳动合同法第二十一条 with high VSN but detectedDomain=UNKNOWN.
            "LABOR_DISPUTE", List.of("劳动", "劳动合同", "工资", "加班", "辞退", "裁员", "社保", "工伤", "离职", "劳动仲裁", "经济补偿", "试用期"),
            "HOUSE_RENTAL", List.of("租房", "房租", "押金", "房东", "租客", "租赁", "退租", "转租", "中介", "房屋", "维修"),
            "CONSUMER_RIGHTS", List.of("消费者", "退款", "退货", "商品", "商家", "售后", "假货", "虚假宣传", "质量", "三包", "网购")
    );

    private static final double VECTOR_WEIGHT = 0.70;
    private static final double KEYWORD_WEIGHT = 0.20;
    private static final double DOMAIN_WEIGHT = 0.10;

    /** 3A 已验收锁定：Basic RAG 第一阶段 Qdrant 候选数 = 10。不得改动。 */
    private static final int BASIC_CANDIDATE_LIMIT = 10;

    /**
     * 4A-EnhancedCandidateRecall：仅 Enhanced 的第一阶段 Qdrant 候选数 = 100。
     * 依据（2026-08-13 真实诊断）：第五百八十五条 raw rank 43~85（R6/R3），
     * 20/30/50 无法稳定覆盖；100 覆盖当前观测 worst case=85，且正式知识库仅
     * 376 条，计算规模很小。锁定 100，不动态扩张。仅 Enhanced 路径使用。
     */
    private static final int ENHANCED_CANDIDATE_LIMIT = 100;

    /** 最终 rerank 输出条数（Basic/Enhanced 共享）。 */
    private static final int FINAL_TOP_K = 5;

    /** Basic response API 契约：top10Hits = 候选内 raw cosine 前 10（3A 原语义）。 */
    private static final int RAW_TOP10_DISPLAY = 10;

    /**
     * Relevance gate (3A-GateLock, director-approved): evidence sufficiency, not question legality.
     * Pass requires detectedDomain != UNKNOWN AND top1.finalScore >= this threshold.
     * Fixed two-condition rule — no VSN/raw/concept-count/LLM conditions.
     */
    private static final double RELEVANCE_MIN_FINAL_SCORE = 0.65;

    /** Fixed relevance reasons exposed via the API. */
    private static final String REASON_PASS = "PASS";
    private static final String REASON_UNKNOWN_DOMAIN = "UNKNOWN_DOMAIN";
    private static final String REASON_LOW_RELEVANCE = "LOW_RELEVANCE";
    private static final String REASON_NO_RESULT = "NO_RESULT";

    /** Matches "第X条" where X is Chinese numerals or Arabic digits. */
    private static final Pattern ARTICLE_NUMBER_PATTERN = Pattern.compile("第[一二三四五六七八九十百千零]+条|第\\d+条");

    /** ARTICLE_NUMBER_MATCH concept name for optional per-article bonus. */
    private static final String ARTICLE_NUMBER_CONCEPT = "ARTICLE_NUMBER_MATCH";

    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final LegalKnowledgeService legalKnowledgeService;
    private final OperationLogService operationLogService;

    public RagServiceImpl(EmbeddingService embeddingService,
                          QdrantService qdrantService,
                          LegalKnowledgeService legalKnowledgeService,
                          OperationLogService operationLogService) {
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.legalKnowledgeService = legalKnowledgeService;
        this.operationLogService = operationLogService;
    }

    /** Basic (3A locked): first-stage Qdrant candidate limit = 10. */
    @Override
    public RagRetrieveTestResponse retrieveTest(String question, Long userId, String username, String ip) {
        return retrieveInternal(question, BASIC_CANDIDATE_LIMIT, userId, username, ip);
    }

    /** Enhanced (4A-EnhancedCandidateRecall): first-stage Qdrant candidate limit = 100. */
    @Override
    public RagRetrieveTestResponse retrieveEnhanced(String question, Long userId, String username, String ip) {
        return retrieveInternal(question, ENHANCED_CANDIDATE_LIMIT, userId, username, ip);
    }

    /**
     * Single shared retrieval pipeline. Basic and Enhanced differ ONLY in the
     * first-stage Qdrant candidateLimit; everything downstream is shared:
     * MySQL authoritative JOIN, KeywordConceptGroups scoring, domain score,
     * 0.70/0.20/0.10 weights, final sorting, Top-5, 0.65 relevance gate.
     */
    private RagRetrieveTestResponse retrieveInternal(String question, int candidateLimit,
                                                     Long userId, String username, String ip) {
        // ---- Stage 1: Embedding (real embedding-3 API call) ----
        float[] queryVector;
        try {
            queryVector = embeddingService.embed(question);
        } catch (Exception e) {
            log.error("RAG retrieve failed at Embedding stage", e);
            logFailure(userId, username, "失败:Embedding阶段:" + shortMessage(e), ip);
            throw new BusinessException(500, "检索失败:Embedding 阶段异常");
        }

        // ---- Stage 2: Qdrant real search with the explicit candidate limit (no MySQL LIKE fallback) ----
        log.info("RAG_QDRANT_SEARCH candidateLimit={} userId={}", candidateLimit, userId);
        List<QdrantSearchResult> candidates;
        try {
            candidates = qdrantService.searchPoints(queryVector, candidateLimit);
        } catch (Exception e) {
            log.error("RAG retrieve failed at Qdrant stage", e);
            logFailure(userId, username, "失败:Qdrant阶段:" + shortMessage(e), ip);
            throw new BusinessException(500, "检索失败:Qdrant 检索异常");
        }

        // Basic response API contract: top10Hits = first 10 raw-cosine candidates.
        // For Basic (limit=10) this is exactly the 3A Top-10; for Enhanced it is
        // only a display slice — the Enhanced response does not use this field.
        List<RagTop10Hit> top10Hits = candidates.stream()
                .limit(RAW_TOP10_DISPLAY)
                .map(h -> new RagTop10Hit(h.getPointId(), h.getScore()))
                .toList();

        if (candidates.isEmpty()) {
            logSuccess(userId, username, UNKNOWN_DOMAIN, List.of(), "成功:Top5=", ip);
            return new RagRetrieveTestResponse(UNKNOWN_DOMAIN, List.of(), top10Hits, List.of(),
                    false, REASON_NO_RESULT);
        }

        // ---- Stage 3: MySQL JOIN for authoritative data ----
        List<Long> articleIds = candidates.stream()
                .map(QdrantSearchResult::getPointId)
                .toList();
        List<LegalArticleDocument> articles;
        try {
            articles = legalKnowledgeService.selectValidArticlesWithDocuments(articleIds);
        } catch (Exception e) {
            log.error("RAG retrieve-test failed at MySQL stage", e);
            logFailure(userId, username, "失败:MySQL阶段:" + shortMessage(e), ip);
            throw new BusinessException(500, "检索失败:MySQL 数据获取异常");
        }

        Map<Long, LegalArticleDocument> articleById = new HashMap<>();
        for (LegalArticleDocument a : articles) {
            articleById.put(a.getArticleId(), a);
        }

        // ---- Stage 0a: Determine query concepts (before scoring) ----
        List<String> queryConcepts = new ArrayList<>();
        for (ConceptGroup cg : KeywordConceptGroups.CONCEPTS) {
            if (cg.matchesQuestion(question)) {
                queryConcepts.add(cg.getConceptName());
            }
        }
        // ARTICLE_NUMBER_MATCH: if question contains "第X条", add the concept
        boolean hasArticleRef = ARTICLE_NUMBER_PATTERN.matcher(question).find();
        if (hasArticleRef) {
            queryConcepts.add(ARTICLE_NUMBER_CONCEPT);
        }

        // ---- Weighted ranking ----
        String detectedDomain = detectDomain(question);
        List<RankedHit> ranked = new ArrayList<>();
        for (QdrantSearchResult hit : candidates) {
            LegalArticleDocument article = articleById.get(hit.getPointId());
            if (article == null) {
                continue; // not valid in MySQL -> excluded from ranking (still in raw candidates)
            }
            double vsn = clamp((hit.getScore() + 1.0) / 2.0, 0.0, 1.0);
            ConceptScoreResult csr = keywordScore(question, queryConcepts, article);
            double domainScore = UNKNOWN_DOMAIN.equals(detectedDomain) ? 0.0
                    : (detectedDomain.equals(article.getDomain()) ? 1.0 : 0.0);
            double finalScore = VECTOR_WEIGHT * vsn + KEYWORD_WEIGHT * csr.score + DOMAIN_WEIGHT * domainScore;
            ranked.add(new RankedHit(hit.getPointId(), vsn, csr.score, domainScore, finalScore,
                    hit.getScore(), csr.matchedConcepts));
        }

        ranked.sort((a, b) -> {
            int c = Double.compare(b.finalScore, a.finalScore);
            if (c != 0) {
                return c;
            }
            return Double.compare(b.rawVectorScore, a.rawVectorScore);
        });

        List<RagTop5Hit> top5 = ranked.stream()
                .limit(FINAL_TOP_K)
                .map(r -> {
                    LegalArticleDocument doc = articleById.get(r.articleId);
                    String docName = doc != null ? doc.getDocumentName() : "";
                    String artNum = doc != null ? doc.getArticleNumber() : "";
                    return new RagTop5Hit(r.articleId, docName, artNum,
                            r.vectorScoreNormalized, r.keywordScore,
                            r.domainScore, r.finalScore, r.matchedConcepts);
                })
                .toList();

        List<Long> top5Ids = top5.stream().map(RagTop5Hit::getArticleId).toList();
        logSuccess(userId, username, detectedDomain, top5Ids, "成功:Top5=", ip);

        RelevanceResult gate = evaluateRelevance(detectedDomain, top5);
        return new RagRetrieveTestResponse(detectedDomain, queryConcepts, top10Hits, top5,
                gate.passed, gate.reason);
    }

    /**
     * Evidence-sufficiency gate over the final ranked Top-1.
     * Order: NO_RESULT -> UNKNOWN_DOMAIN -> LOW_RELEVANCE -> PASS.
     * Real retrieval failures never reach here (they throw as HTTP 500 upstream).
     */
    private RelevanceResult evaluateRelevance(String detectedDomain, List<RagTop5Hit> top5) {
        if (top5.isEmpty()) {
            return new RelevanceResult(false, REASON_NO_RESULT);
        }
        if (UNKNOWN_DOMAIN.equals(detectedDomain)) {
            return new RelevanceResult(false, REASON_UNKNOWN_DOMAIN);
        }
        double top1Final = top5.get(0).getFinalScore();
        if (top1Final < RELEVANCE_MIN_FINAL_SCORE) {
            return new RelevanceResult(false, REASON_LOW_RELEVANCE);
        }
        return new RelevanceResult(true, REASON_PASS);
    }

    private static class RelevanceResult {
        final boolean passed;
        final String reason;

        RelevanceResult(boolean passed, String reason) {
            this.passed = passed;
            this.reason = reason;
        }
    }

    // ---- domain detection ----

    private String detectDomain(String question) {
        int maxHits = 0;
        List<String> leaders = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
            int hits = 0;
            for (String kw : entry.getValue()) {
                if (question.contains(kw)) {
                    hits++;
                }
            }
            if (hits > maxHits) {
                maxHits = hits;
                leaders.clear();
                leaders.add(entry.getKey());
            } else if (hits == maxHits && hits > 0) {
                leaders.add(entry.getKey());
            }
        }
        // maxHits == 0 -> UNKNOWN; tie -> UNKNOWN
        if (maxHits == 0 || leaders.size() != 1) {
            return UNKNOWN_DOMAIN;
        }
        return leaders.get(0);
    }

    // ---- keyword scoring (3A-K: concept-group based) ----

    /**
     * Concept-group-based keywordScore.
     * question: original user question (for ARTICLE_NUMBER_MATCH verification)
     * queryConcepts: set of concept names triggered by the question (pre-computed, includes ARTICLE_NUMBER_MATCH if applicable)
     * article: the legal article to score against
     *
     * keywordScore = matchedConceptCount / queryConceptCount; queryConceptCount==0 -> 0.0.
     * Result clamped to [0.0, 1.0], no NaN/Infinity.
     */
    private ConceptScoreResult keywordScore(String question, List<String> queryConcepts, LegalArticleDocument article) {
        int qc = queryConcepts.size();
        if (qc == 0) {
            return new ConceptScoreResult(0.0, List.of());
        }

        List<String> matched = new ArrayList<>();
        for (String conceptName : queryConcepts) {
            if (ARTICLE_NUMBER_CONCEPT.equals(conceptName)) {
                if (articleNumberStrictMatch(question, article.getArticleNumber())) {
                    matched.add(ARTICLE_NUMBER_CONCEPT);
                }
                continue;
            }

            ConceptGroup cg = findConceptByName(conceptName);
            if (cg == null) continue;

            if (cg.matchesEvidence(article.getContent(), article.getDocumentName())) {
                matched.add(conceptName);
            }
        }

        double score = (double) matched.size() / qc;
        return new ConceptScoreResult(clamp(score, 0.0, 1.0), List.copyOf(matched));
    }

    private ConceptGroup findConceptByName(String conceptName) {
        for (ConceptGroup cg : KeywordConceptGroups.CONCEPTS) {
            if (cg.getConceptName().equals(conceptName)) {
                return cg;
            }
        }
        return null;
    }

    /**
     * Strict literal match: question contains "第X条" and article.articleNumber.equals("第X条").
     */
    private boolean articleNumberStrictMatch(String question, String articleNumber) {
        if (articleNumber == null || articleNumber.isBlank()) {
            return false;
        }
        Matcher m = ARTICLE_NUMBER_PATTERN.matcher(question);
        while (m.find()) {
            if (articleNumber.equals(m.group())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Holds both the numeric score and the list of matched concept names for transparency.
     */
    private static class ConceptScoreResult {
        final double score;
        final List<String> matchedConcepts;

        ConceptScoreResult(double score, List<String> matchedConcepts) {
            this.score = score;
            this.matchedConcepts = matchedConcepts;
        }
    }

    // ---- helpers ----

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String shortMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }

    private void logSuccess(Long userId, String username, String detectedDomain, List<Long> top5Ids, String prefix, String ip) {
        try {
            String top5Str = top5Ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            operationLogService.log(userId, username, "RAG_RETRIEVE_TEST", detectedDomain,
                    prefix + top5Str, ip);
        } catch (Exception logEx) {
            log.warn("Failed to write RAG_RETRIEVE_TEST operation log", logEx);
        }
    }

    private void logFailure(Long userId, String username, String result, String ip) {
        try {
            operationLogService.logFailure(userId, username, "RAG_RETRIEVE_TEST", UNKNOWN_DOMAIN, result, ip);
        } catch (Exception logEx) {
            log.warn("Failed to write RAG_RETRIEVE_TEST failure log", logEx);
        }
    }

    private static class RankedHit {
        final long articleId;
        final double vectorScoreNormalized;
        final double keywordScore;
        final double domainScore;
        final double finalScore;
        final double rawVectorScore;
        final List<String> matchedConcepts;

        RankedHit(long articleId, double vectorScoreNormalized, double keywordScore,
                  double domainScore, double finalScore, double rawVectorScore,
                  List<String> matchedConcepts) {
            this.articleId = articleId;
            this.vectorScoreNormalized = vectorScoreNormalized;
            this.keywordScore = keywordScore;
            this.domainScore = domainScore;
            this.finalScore = finalScore;
            this.rawVectorScore = rawVectorScore;
            this.matchedConcepts = matchedConcepts;
        }
    }
}