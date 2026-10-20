package com.ailegal.dto;

public class ArticleUpdateRequest {

    private String articleNumber;
    private String content;

    public String getArticleNumber() { return articleNumber; }
    public void setArticleNumber(String articleNumber) { this.articleNumber = articleNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
