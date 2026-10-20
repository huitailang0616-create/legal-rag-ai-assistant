package com.ailegal.dto;

/**
 * JOIN result of legal_article + legal_document (both valid_status = 1).
 * This is the authoritative legal data source for RAG scoring.
 */
public class LegalArticleDocument {

    private Long articleId;
    private Long documentId;
    private String articleNumber;
    private String content;
    private Integer articleValidStatus;
    private String documentName;
    private String domain;
    private String documentType;
    private String source;
    private Integer documentValidStatus;

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public void setArticleNumber(String articleNumber) {
        this.articleNumber = articleNumber;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getArticleValidStatus() {
        return articleValidStatus;
    }

    public void setArticleValidStatus(Integer articleValidStatus) {
        this.articleValidStatus = articleValidStatus;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getDocumentValidStatus() {
        return documentValidStatus;
    }

    public void setDocumentValidStatus(Integer documentValidStatus) {
        this.documentValidStatus = documentValidStatus;
    }
}