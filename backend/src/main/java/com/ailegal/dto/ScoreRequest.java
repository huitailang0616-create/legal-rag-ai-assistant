package com.ailegal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 人工评分请求。四个 score 允许 NULL（未评分），否则必须 1~5。
 * 方向固定：answerRelevance / legalCorrectness / citationCompleteness 1差→5好；
 * hallucination 1=无明显幻觉 → 5=严重幻觉（方向相反）。
 * manualComment 物理上限 varchar(500)（与已执行 DDL 一致）。
 */
public class ScoreRequest {

    @Min(value = 1, message = "评分范围1-5")
    @Max(value = 5, message = "评分范围1-5")
    private Integer answerRelevanceScore;

    @Min(value = 1, message = "评分范围1-5")
    @Max(value = 5, message = "评分范围1-5")
    private Integer legalCorrectnessScore;

    @Min(value = 1, message = "评分范围1-5")
    @Max(value = 5, message = "评分范围1-5")
    private Integer citationCompletenessScore;

    @Min(value = 1, message = "评分范围1-5")
    @Max(value = 5, message = "评分范围1-5")
    private Integer hallucinationScore;

    @Size(max = 500, message = "备注最长500字")
    private String manualComment;

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
