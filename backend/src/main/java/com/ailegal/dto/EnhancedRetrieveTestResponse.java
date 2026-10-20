package com.ailegal.dto;

import java.util.List;

/**
 * Response of POST /api/admin/rag/enhanced-retrieve-test (4A technical acceptance
 * API, ADMIN only, no frontend entry). Shows the full understanding + retrieval
 * chain WITHOUT calling the Answer LLM. Fields are acceptance-oriented:
 * legalIntent / supportedDomain / rewrittenQuery / keyFacts / retrievalQuery /
 * basicDetectedDomain / queryConcepts / top5 (full reranked Top-5, top1 = top5[0]) /
 * relevancePassed / enhancedReason, plus per-stage timings.
 * No question/prompt/answer is persisted anywhere.
 */
public class EnhancedRetrieveTestResponse {

    private boolean legalIntent;
    private String supportedDomain;
    private String rewrittenQuery;
    private List<String> keyFacts;
    private String retrievalQuery;
    private String basicDetectedDomain;
    private List<String> queryConcepts;
    private List<RagTop5Hit> top5;
    private RagTop5Hit top1;
    private boolean relevancePassed;
    private String enhancedReason;
    private String basicReason;      // underlying Basic RAG reason (e.g. LOW_RELEVANCE) when gate rejects
    private long queryUnderstandingMs;
    private long ragMs;
    private long totalMs;

    public EnhancedRetrieveTestResponse() {
    }

    public boolean isLegalIntent() {
        return legalIntent;
    }

    public void setLegalIntent(boolean legalIntent) {
        this.legalIntent = legalIntent;
    }

    public String getSupportedDomain() {
        return supportedDomain;
    }

    public void setSupportedDomain(String supportedDomain) {
        this.supportedDomain = supportedDomain;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
    }

    public String getRetrievalQuery() {
        return retrievalQuery;
    }

    public void setRetrievalQuery(String retrievalQuery) {
        this.retrievalQuery = retrievalQuery;
    }

    public String getBasicDetectedDomain() {
        return basicDetectedDomain;
    }

    public void setBasicDetectedDomain(String basicDetectedDomain) {
        this.basicDetectedDomain = basicDetectedDomain;
    }

    public List<String> getQueryConcepts() {
        return queryConcepts;
    }

    public void setQueryConcepts(List<String> queryConcepts) {
        this.queryConcepts = queryConcepts;
    }

    public List<RagTop5Hit> getTop5() {
        return top5;
    }

    public void setTop5(List<RagTop5Hit> top5) {
        this.top5 = top5;
    }

    public RagTop5Hit getTop1() {
        return top1;
    }

    public void setTop1(RagTop5Hit top1) {
        this.top1 = top1;
    }

    public boolean isRelevancePassed() {
        return relevancePassed;
    }

    public void setRelevancePassed(boolean relevancePassed) {
        this.relevancePassed = relevancePassed;
    }

    public String getEnhancedReason() {
        return enhancedReason;
    }

    public void setEnhancedReason(String enhancedReason) {
        this.enhancedReason = enhancedReason;
    }

    public String getBasicReason() {
        return basicReason;
    }

    public void setBasicReason(String basicReason) {
        this.basicReason = basicReason;
    }

    public long getQueryUnderstandingMs() {
        return queryUnderstandingMs;
    }

    public void setQueryUnderstandingMs(long queryUnderstandingMs) {
        this.queryUnderstandingMs = queryUnderstandingMs;
    }

    public long getRagMs() {
        return ragMs;
    }

    public void setRagMs(long ragMs) {
        this.ragMs = ragMs;
    }

    public long getTotalMs() {
        return totalMs;
    }

    public void setTotalMs(long totalMs) {
        this.totalMs = totalMs;
    }
}
