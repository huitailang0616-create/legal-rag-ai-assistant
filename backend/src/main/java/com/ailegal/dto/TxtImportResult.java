package com.ailegal.dto;

public class TxtImportResult {

    private boolean success;
    private String message;
    private Long documentId;
    private String documentName;
    private int articleCount;
    private int segmentCount;

    public static TxtImportResult success(Long documentId, String documentName, int articleCount) {
        TxtImportResult r = new TxtImportResult();
        r.success = true;
        r.message = "导入成功";
        r.documentId = documentId;
        r.documentName = documentName;
        r.articleCount = articleCount;
        r.segmentCount = articleCount;
        return r;
    }

    public static TxtImportResult failure(String message) {
        TxtImportResult r = new TxtImportResult();
        r.success = false;
        r.message = message;
        r.articleCount = 0;
        r.segmentCount = 0;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public int getArticleCount() { return articleCount; }
    public void setArticleCount(int articleCount) { this.articleCount = articleCount; }
    public int getSegmentCount() { return segmentCount; }
    public void setSegmentCount(int segmentCount) { this.segmentCount = segmentCount; }
}
