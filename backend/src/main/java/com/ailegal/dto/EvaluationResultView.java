package com.ailegal.dto;

/**
 * 评测结果展示项（结果表 + 查看弹窗用），JOIN question 信息。
 */
public class EvaluationResultView {

    private Long id;
    private String questionCode;
    private String domain;
    private String question;
    private String mode;
    private String answer;
    private Integer retrievalHit;
    private String retrievedArticleIds;
    private String citationsJson;
    /** 人工参考法条 JSON（原样，前端格式化为《法律名称》第X条） */
    private String referenceArticlesJson;
    /** run 级命中口径：LEGACY_KEYWORD_V1 / REFERENCE_ARTICLE_TOP5_V2 */
    private String metricVersion;
    private Integer responseTimeMs;
    private String executionStatus;
    private String errorCode;
    private Integer answerRelevanceScore;
    private Integer legalCorrectnessScore;
    private Integer citationCompletenessScore;
    private Integer hallucinationScore;
    private String manualComment;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestionCode() { return questionCode; }
    public void setQuestionCode(String questionCode) { this.questionCode = questionCode; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
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
    public String getReferenceArticlesJson() { return referenceArticlesJson; }
    public void setReferenceArticlesJson(String referenceArticlesJson) { this.referenceArticlesJson = referenceArticlesJson; }
    public String getMetricVersion() { return metricVersion; }
    public void setMetricVersion(String metricVersion) { this.metricVersion = metricVersion; }
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
}
