package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 评测固定题目（模块6）。
 * question_code 全局唯一：L01-L10 / R01-R10 / C01-C10。
 */
@TableName("evaluation_question")
public class EvaluationQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String questionCode;
    private String domain;
    private String questionText;
    /** JSON 字符串数组，如 ["未签订书面劳动合同","双倍工资"]；辅助关键词（历史兼容，不参与正式 Hit@5） */
    private String expectedEvidenceKeywords;
    /** 人工标注参考法条 JSON：[{"documentName":"...","articleNumber":"..."}]；REFERENCE_ARTICLE_TOP5_V2 命中判定依据 */
    private String referenceArticlesJson;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestionCode() { return questionCode; }
    public void setQuestionCode(String questionCode) { this.questionCode = questionCode; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getExpectedEvidenceKeywords() { return expectedEvidenceKeywords; }
    public void setExpectedEvidenceKeywords(String expectedEvidenceKeywords) { this.expectedEvidenceKeywords = expectedEvidenceKeywords; }
    public String getReferenceArticlesJson() { return referenceArticlesJson; }
    public void setReferenceArticlesJson(String referenceArticlesJson) { this.referenceArticlesJson = referenceArticlesJson; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
