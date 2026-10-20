package com.ailegal.dto;

/**
 * 运行记录详情中的 Top5 检索条款可读视图（ADMIN）。
 * 按 retrieved_article_ids 反查当前 MySQL authoritative article；历史条款已失效/不存在时
 * missing=true（前端显示"条款已不存在或已失效"），详情接口绝不因缺失而 500。
 */
public class RuntimeArticleView {

    private Long articleId;
    private String documentName;
    private String articleNumber;
    private String content;
    private boolean missing;

    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getArticleNumber() { return articleNumber; }
    public void setArticleNumber(String articleNumber) { this.articleNumber = articleNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isMissing() { return missing; }
    public void setMissing(boolean missing) { this.missing = missing; }
}
