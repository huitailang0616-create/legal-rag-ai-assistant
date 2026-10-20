package com.ailegal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 咨询运行记录详情（ADMIN）：完整字段 + 可读的 Top5 条款视图 + 解析后的引用。
 * FAILED 记录可能无 answer / 无 retrieval —— 对应字段为 null/空列表，前端须兼容。
 */
public class ConsultRuntimeRecordDetailResponse {

    private Long id;
    private Long userId;
    private String username;
    private String question;
    private String domain;
    private String retrievalMode;
    private String fallbackReason;
    private String relevanceReason;
    private List<RuntimeArticleView> top5Articles;
    private List<ConsultCitation> citations;
    private String answer;
    private Integer responseTimeMs;
    private String executionStatus;
    private String errorCode;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public String getRelevanceReason() { return relevanceReason; }
    public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }
    public List<RuntimeArticleView> getTop5Articles() { return top5Articles; }
    public void setTop5Articles(List<RuntimeArticleView> top5Articles) { this.top5Articles = top5Articles; }
    public List<ConsultCitation> getCitations() { return citations; }
    public void setCitations(List<ConsultCitation> citations) { this.citations = citations; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
