package com.ailegal.dto;

import java.util.List;

/**
 * Response of POST /api/admin/rag/retrieve-test (3A calibration API).
 * top10: real Qdrant Top-10 hits (articleId + raw vector score).
 * top5:  weighted ranking result (Top-5 subset of Top-10).
 * relevancePassed/relevanceReason: evidence-sufficiency gate computed from top1 final score.
 *   PASS / UNKNOWN_DOMAIN / LOW_RELEVANCE / NO_RESULT — real exceptions still surface as HTTP 500.
 */
public class RagRetrieveTestResponse {

    private String detectedDomain;
    private List<String> queryConcepts;
    private List<RagTop10Hit> top10;
    private List<RagTop5Hit> top5;
    private boolean relevancePassed;
    private String relevanceReason;

    public RagRetrieveTestResponse() {
    }

    public RagRetrieveTestResponse(String detectedDomain, List<String> queryConcepts,
                                   List<RagTop10Hit> top10, List<RagTop5Hit> top5,
                                   boolean relevancePassed, String relevanceReason) {
        this.detectedDomain = detectedDomain;
        this.queryConcepts = queryConcepts;
        this.top10 = top10;
        this.top5 = top5;
        this.relevancePassed = relevancePassed;
        this.relevanceReason = relevanceReason;
    }

    public String getDetectedDomain() {
        return detectedDomain;
    }

    public void setDetectedDomain(String detectedDomain) {
        this.detectedDomain = detectedDomain;
    }

    public List<String> getQueryConcepts() {
        return queryConcepts;
    }

    public void setQueryConcepts(List<String> queryConcepts) {
        this.queryConcepts = queryConcepts;
    }

    public List<RagTop10Hit> getTop10() {
        return top10;
    }

    public void setTop10(List<RagTop10Hit> top10) {
        this.top10 = top10;
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

    public String getRelevanceReason() {
        return relevanceReason;
    }

    public void setRelevanceReason(String relevanceReason) {
        this.relevanceReason = relevanceReason;
    }
}
