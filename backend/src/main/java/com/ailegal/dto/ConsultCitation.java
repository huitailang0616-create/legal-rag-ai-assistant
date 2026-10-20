package com.ailegal.dto;

/**
 * Backend-generated citation for /api/consult.
 * Always derived from the actual Top-5 MySQL data passed to the LLM,
 * in the same order as evidence blocks 1..5. Never parsed from the model answer.
 */
public class ConsultCitation {

    private Long articleId;
    private Long documentId;
    private String documentName;
    private String articleNumber;
    private String source;
    private String contentSnippet;

    public ConsultCitation() {
    }

    public ConsultCitation(Long articleId, Long documentId, String documentName,
                           String articleNumber, String source, String contentSnippet) {
        this.articleId = articleId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.articleNumber = articleNumber;
        this.source = source;
        this.contentSnippet = contentSnippet;
    }

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

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public void setArticleNumber(String articleNumber) {
        this.articleNumber = articleNumber;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContentSnippet() {
        return contentSnippet;
    }

    public void setContentSnippet(String contentSnippet) {
        this.contentSnippet = contentSnippet;
    }
}
