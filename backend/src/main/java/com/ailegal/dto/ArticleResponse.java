package com.ailegal.dto;

import com.ailegal.entity.LegalArticle;

import java.time.LocalDateTime;

public class ArticleResponse {

    private Long id;
    private Long documentId;
    private String articleNumber;
    private String content;
    private String domain;
    private String source;
    private Integer validStatus;
    private String vectorStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArticleResponse fromEntity(LegalArticle a) {
        ArticleResponse r = new ArticleResponse();
        r.id = a.getId();
        r.documentId = a.getDocumentId();
        r.articleNumber = a.getArticleNumber();
        r.content = a.getContent();
        r.domain = a.getDomain();
        r.source = a.getSource();
        r.validStatus = a.getValidStatus();
        r.vectorStatus = a.getVectorStatus();
        r.createdAt = a.getCreatedAt();
        r.updatedAt = a.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getArticleNumber() { return articleNumber; }
    public void setArticleNumber(String articleNumber) { this.articleNumber = articleNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getValidStatus() { return validStatus; }
    public void setValidStatus(Integer validStatus) { this.validStatus = validStatus; }
    public String getVectorStatus() { return vectorStatus; }
    public void setVectorStatus(String vectorStatus) { this.vectorStatus = vectorStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
