package com.ailegal.dto;

import java.util.List;

/**
 * Full enhanced-stage result (4A): Query Understanding + Basic RAG, up to the gate.
 * Shared by /api/consult (then feeds the Answer LLM on PASS) and the ADMIN-only
 * /api/admin/rag/enhanced-retrieve-test technical API.
 *
 * enhancedReason is one of: PASS / NON_LEGAL_INTENT / UNSUPPORTED_DOMAIN / KNOWLEDGE_GAP.
 * retrievalQuery is the internal-only retrieval string (canonical domain term +
 * rewrittenQuery) — never shown to end users, never passed to the Answer LLM,
 * never stored in operation_log.
 */
public class EnhancedRetrievalResult {

    private boolean legalIntent;
    private String supportedDomain;      // understanding domain
    private String rewrittenQuery;
    private List<String> keyFacts;
    private String retrievalQuery;       // internal only
    private String basicDetectedDomain;  // Basic RAG domain detector output
    private List<String> queryConcepts;
    private List<RagTop5Hit> top5;
    private boolean relevancePassed;
    private String enhancedReason;
    private String basicReason;        // underlying Basic RAG reason, for audit logging only
    private boolean fallbackToBasic;   // Demo-Reliability-1: QU technical failure -> Basic RAG
    private long queryUnderstandingMs;
    private long ragMs;
    private long totalMs;

    public EnhancedRetrievalResult() {
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

    public boolean isFallbackToBasic() {
        return fallbackToBasic;
    }

    public void setFallbackToBasic(boolean fallbackToBasic) {
        this.fallbackToBasic = fallbackToBasic;
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
