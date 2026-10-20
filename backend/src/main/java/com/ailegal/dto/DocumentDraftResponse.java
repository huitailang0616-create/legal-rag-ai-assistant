package com.ailegal.dto;

import com.ailegal.entity.DocumentDraft;

import java.time.LocalDateTime;

/**
 * 文书草稿响应。不暴露 userId / deleted。
 * caseTitle：关联案件的标题，仅作展示；为 null 表示未绑定案件。
 * content：NULL 表示尚未生成正文（5A 恒为 NULL）。
 */
public class DocumentDraftResponse {

    private Long id;
    private String title;
    private String documentType;
    private String domain;
    private Long caseAnalysisId;
    private String caseTitle;
    private String generationStatus;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentDraftResponse fromEntity(DocumentDraft d) {
        DocumentDraftResponse r = new DocumentDraftResponse();
        r.id = d.getId();
        r.title = d.getTitle();
        r.documentType = d.getDocumentType();
        r.domain = d.getDomain();
        r.caseAnalysisId = d.getCaseAnalysisId();
        r.generationStatus = d.getGenerationStatus();
        r.content = d.getContent();
        r.createdAt = d.getCreatedAt();
        r.updatedAt = d.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public Long getCaseAnalysisId() { return caseAnalysisId; }
    public void setCaseAnalysisId(Long caseAnalysisId) { this.caseAnalysisId = caseAnalysisId; }
    public String getCaseTitle() { return caseTitle; }
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }
    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
