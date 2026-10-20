package com.ailegal.dto;

import com.ailegal.entity.LegalDocument;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DocumentResponse {

    private Long id;
    private String documentName;
    private String domain;
    private String documentType;
    private LocalDate effectiveDate;
    private String source;
    private Integer validStatus;
    private Integer articleCount;
    private String vectorStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentResponse fromEntity(LegalDocument doc) {
        DocumentResponse r = new DocumentResponse();
        r.id = doc.getId();
        r.documentName = doc.getDocumentName();
        r.domain = doc.getDomain();
        r.documentType = doc.getDocumentType();
        r.effectiveDate = doc.getEffectiveDate();
        r.source = doc.getSource();
        r.validStatus = doc.getValidStatus();
        r.articleCount = doc.getArticleCount();
        r.vectorStatus = doc.getVectorStatus();
        r.createdAt = doc.getCreatedAt();
        r.updatedAt = doc.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getValidStatus() { return validStatus; }
    public void setValidStatus(Integer validStatus) { this.validStatus = validStatus; }
    public Integer getArticleCount() { return articleCount; }
    public void setArticleCount(Integer articleCount) { this.articleCount = articleCount; }
    public String getVectorStatus() { return vectorStatus; }
    public void setVectorStatus(String vectorStatus) { this.vectorStatus = vectorStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
