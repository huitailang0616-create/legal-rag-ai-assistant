package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 正式法律咨询运行记录（Final-Requirement-Gaps-B）。
 * 管理员遥测表：POST /api/consult 每次服务级执行一行，终态一次性写入，无更新/删除 API。
 * 与 consultation_history 分工：history 只存用户得到的回答（SUCCESS/业务 REJECTED），
 * 本表存全部执行（含 Provider/QU/Answer FAILED）。记录写入失败不影响咨询结果
 * （saveQuietly 吞异常 + warn，telemetry 语义，与 history 的"方案 A"相反）。
 * JSON 列（retrievedArticleIds / citationsJson）以 String 承载（ConsultationHistory 先例）。
 * 禁止保存：prompt / embedding / raw provider response / API key / rewrittenQuery / debug dump。
 */
@TableName("consult_runtime_record")
public class ConsultRuntimeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String question;
    private String domain;
    private String retrievalMode;
    private String fallbackReason;
    private String relevanceReason;
    private String retrievedArticleIds;
    private String citationsJson;
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
    public String getRetrievedArticleIds() { return retrievedArticleIds; }
    public void setRetrievedArticleIds(String retrievedArticleIds) { this.retrievedArticleIds = retrievedArticleIds; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
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
