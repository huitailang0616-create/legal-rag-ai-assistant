package com.ailegal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 咨询历史详情（Final-System-Closeout-A）。
 * citations 为后端生成的 ConsultCitation 列表（与 /api/consult 响应一致）。
 * 不暴露 userId / deleted / 内部检索信息。
 */
public class ConsultationHistoryDetailResponse {

    private Long id;
    private String question;
    private String domain;
    private String answer;
    private List<ConsultCitation> citations;
    private boolean evidenceInsufficient;
    private String relevanceReason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<ConsultCitation> getCitations() { return citations; }
    public void setCitations(List<ConsultCitation> citations) { this.citations = citations; }
    public boolean isEvidenceInsufficient() { return evidenceInsufficient; }
    public void setEvidenceInsufficient(boolean evidenceInsufficient) { this.evidenceInsufficient = evidenceInsufficient; }
    public String getRelevanceReason() { return relevanceReason; }
    public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
