package com.ailegal.dto;

import com.ailegal.entity.ConsultationHistory;

import java.time.LocalDateTime;

/**
 * 咨询历史列表项（Final-System-Closeout-A）。
 * 列表不返回完整长 answer / citations（历史会长期增长，避免一次性传输大字段）；
 * 详情经 GET /api/consultations/{id} 单独获取。不暴露 userId / deleted。
 */
public class ConsultationHistoryListResponse {

    private Long id;
    private String question;
    private String domain;
    private boolean evidenceInsufficient;
    private String relevanceReason;
    private LocalDateTime createdAt;

    public static ConsultationHistoryListResponse fromEntity(ConsultationHistory h) {
        ConsultationHistoryListResponse r = new ConsultationHistoryListResponse();
        r.setId(h.getId());
        r.setQuestion(h.getQuestion());
        r.setDomain(h.getDomain());
        r.setEvidenceInsufficient(h.getEvidenceInsufficient() != null && h.getEvidenceInsufficient() == 1);
        r.setRelevanceReason(h.getRelevanceReason());
        r.setCreatedAt(h.getCreatedAt());
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isEvidenceInsufficient() { return evidenceInsufficient; }
    public void setEvidenceInsufficient(boolean evidenceInsufficient) { this.evidenceInsufficient = evidenceInsufficient; }
    public String getRelevanceReason() { return relevanceReason; }
    public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
