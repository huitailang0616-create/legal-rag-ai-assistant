package com.ailegal.dto;

import com.ailegal.entity.ConsultRuntimeRecord;

import java.time.LocalDateTime;

/**
 * 咨询运行记录列表项（ADMIN）。列表刻意不返回完整 answer / citations / retrieval IDs：
 * 详情接口单独承载，避免列表载荷膨胀。
 */
public class ConsultRuntimeRecordListResponse {

    private Long id;
    private LocalDateTime createdAt;
    private String username;
    private String questionSummary;
    private String domain;
    private String retrievalMode;
    private String executionStatus;
    private Integer responseTimeMs;
    private String errorCode;

    public static ConsultRuntimeRecordListResponse fromEntity(ConsultRuntimeRecord r) {
        ConsultRuntimeRecordListResponse resp = new ConsultRuntimeRecordListResponse();
        resp.setId(r.getId());
        resp.setCreatedAt(r.getCreatedAt());
        resp.setUsername(r.getUsername());
        String q = r.getQuestion() == null ? "" : r.getQuestion();
        resp.setQuestionSummary(q.length() > 60 ? q.substring(0, 60) + "…" : q);
        resp.setDomain(r.getDomain());
        resp.setRetrievalMode(r.getRetrievalMode());
        resp.setExecutionStatus(r.getExecutionStatus());
        resp.setResponseTimeMs(r.getResponseTimeMs());
        resp.setErrorCode(r.getErrorCode());
        return resp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getQuestionSummary() { return questionSummary; }
    public void setQuestionSummary(String questionSummary) { this.questionSummary = questionSummary; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
