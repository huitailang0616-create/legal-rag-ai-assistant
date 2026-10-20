package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 评测结果（模块6）：run × question × mode 一行。
 * 状态机：PENDING -> RUNNING -> SUCCESS / REJECTED / FAILED。
 * REJECTED 是合法系统结果（gate / QU 判定证据不足或不支持），不是 FAILED。
 * hallucination_score: 1=无明显幻觉 … 5=严重幻觉（方向与其他三维相反）。
 */
@TableName("evaluation_result")
public class EvaluationResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long questionId;
    private String mode;
    private String answer;
    /** RAG 方案 = true/false；DIRECT_LLM = NULL（不参与命中率） */
    private Integer retrievalHit;
    /** JSON array string（Top5 article id）；DIRECT_LLM = NULL */
    private String retrievedArticleIds;
    /** JSON array string（backend-generated citations）；gate/QU reject = "[]"；DIRECT_LLM = NULL */
    private String citationsJson;
    /** 端到端耗时（含 Answer LLM 或失败路径） */
    private Integer responseTimeMs;
    private String executionStatus;
    private String errorCode;
    private Integer answerRelevanceScore;
    private Integer legalCorrectnessScore;
    private Integer citationCompletenessScore;
    private Integer hallucinationScore;
    private String manualComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getRetrievalHit() { return retrievalHit; }
    public void setRetrievalHit(Integer retrievalHit) { this.retrievalHit = retrievalHit; }
    public String getRetrievedArticleIds() { return retrievedArticleIds; }
    public void setRetrievedArticleIds(String retrievedArticleIds) { this.retrievedArticleIds = retrievedArticleIds; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getAnswerRelevanceScore() { return answerRelevanceScore; }
    public void setAnswerRelevanceScore(Integer answerRelevanceScore) { this.answerRelevanceScore = answerRelevanceScore; }
    public Integer getLegalCorrectnessScore() { return legalCorrectnessScore; }
    public void setLegalCorrectnessScore(Integer legalCorrectnessScore) { this.legalCorrectnessScore = legalCorrectnessScore; }
    public Integer getCitationCompletenessScore() { return citationCompletenessScore; }
    public void setCitationCompletenessScore(Integer citationCompletenessScore) { this.citationCompletenessScore = citationCompletenessScore; }
    public Integer getHallucinationScore() { return hallucinationScore; }
    public void setHallucinationScore(Integer hallucinationScore) { this.hallucinationScore = hallucinationScore; }
    public String getManualComment() { return manualComment; }
    public void setManualComment(String manualComment) { this.manualComment = manualComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
