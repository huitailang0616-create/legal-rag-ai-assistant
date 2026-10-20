package com.ailegal.dto;

/**
 * 单 mode 统计。所有均值忽略 NULL，并返回样本量。
 */
public class ModeSummary {

    private String mode;
    private long total;
    private long success;
    private long rejected;
    private long failed;

    /** 仅 SUCCESS + REJECTED 且 response_time_ms 非 NULL */
    private Long avgResponseTimeMs;
    private long responseTimeSampleCount;

    /** 仅 BASIC/ENHANCED 有意义；DIRECT 为 0/0 */
    private long retrievalHitCount;
    private long retrievalEvaluatedCount;
    private Double retrievalHitRate;

    private Double avgAnswerRelevance;
    private long answerRelevanceSampleCount;
    private Double avgLegalCorrectness;
    private long legalCorrectnessSampleCount;
    private Double avgCitationCompleteness;
    private long citationCompletenessSampleCount;
    private Double avgHallucination;
    private long hallucinationSampleCount;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getSuccess() { return success; }
    public void setSuccess(long success) { this.success = success; }
    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }
    public Long getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(Long avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }
    public long getResponseTimeSampleCount() { return responseTimeSampleCount; }
    public void setResponseTimeSampleCount(long responseTimeSampleCount) { this.responseTimeSampleCount = responseTimeSampleCount; }
    public long getRetrievalHitCount() { return retrievalHitCount; }
    public void setRetrievalHitCount(long retrievalHitCount) { this.retrievalHitCount = retrievalHitCount; }
    public long getRetrievalEvaluatedCount() { return retrievalEvaluatedCount; }
    public void setRetrievalEvaluatedCount(long retrievalEvaluatedCount) { this.retrievalEvaluatedCount = retrievalEvaluatedCount; }
    public Double getRetrievalHitRate() { return retrievalHitRate; }
    public void setRetrievalHitRate(Double retrievalHitRate) { this.retrievalHitRate = retrievalHitRate; }
    public Double getAvgAnswerRelevance() { return avgAnswerRelevance; }
    public void setAvgAnswerRelevance(Double avgAnswerRelevance) { this.avgAnswerRelevance = avgAnswerRelevance; }
    public long getAnswerRelevanceSampleCount() { return answerRelevanceSampleCount; }
    public void setAnswerRelevanceSampleCount(long answerRelevanceSampleCount) { this.answerRelevanceSampleCount = answerRelevanceSampleCount; }
    public Double getAvgLegalCorrectness() { return avgLegalCorrectness; }
    public void setAvgLegalCorrectness(Double avgLegalCorrectness) { this.avgLegalCorrectness = avgLegalCorrectness; }
    public long getLegalCorrectnessSampleCount() { return legalCorrectnessSampleCount; }
    public void setLegalCorrectnessSampleCount(long legalCorrectnessSampleCount) { this.legalCorrectnessSampleCount = legalCorrectnessSampleCount; }
    public Double getAvgCitationCompleteness() { return avgCitationCompleteness; }
    public void setAvgCitationCompleteness(Double avgCitationCompleteness) { this.avgCitationCompleteness = avgCitationCompleteness; }
    public long getCitationCompletenessSampleCount() { return citationCompletenessSampleCount; }
    public void setCitationCompletenessSampleCount(long citationCompletenessSampleCount) { this.citationCompletenessSampleCount = citationCompletenessSampleCount; }
    public Double getAvgHallucination() { return avgHallucination; }
    public void setAvgHallucination(Double avgHallucination) { this.avgHallucination = avgHallucination; }
    public long getHallucinationSampleCount() { return hallucinationSampleCount; }
    public void setHallucinationSampleCount(long hallucinationSampleCount) { this.hallucinationSampleCount = hallucinationSampleCount; }
}
