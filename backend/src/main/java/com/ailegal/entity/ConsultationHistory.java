package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户法律咨询历史（Final-System-Closeout-A）。
 * 复刻 case_analysis 用户数据范式：user_id NOT NULL + 逻辑删除 deleted + 不建 FK。
 * citations_json 为原生 MySQL JSON 列，实体以 String 承载（CaseAnalysis.extractionJson 先例）。
 * 本表是"回答那一刻的不可变快照"：无更新 API，禁止物理删除。
 */
@TableName("consultation_history")
public class ConsultationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String question;
    private String domain;
    private String answer;
    private Integer evidenceInsufficient;
    private String relevanceReason;
    private String citationsJson;
    private LocalDateTime createdAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getEvidenceInsufficient() { return evidenceInsufficient; }
    public void setEvidenceInsufficient(Integer evidenceInsufficient) { this.evidenceInsufficient = evidenceInsufficient; }
    public String getRelevanceReason() { return relevanceReason; }
    public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
