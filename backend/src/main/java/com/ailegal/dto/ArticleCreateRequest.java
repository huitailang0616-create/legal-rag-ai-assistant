package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;

public class ArticleCreateRequest {

    @NotBlank(message = "条款编号不能为空")
    private String articleNumber;

    @NotBlank(message = "条款正文不能为空")
    private String content;

    public String getArticleNumber() { return articleNumber; }
    public void setArticleNumber(String articleNumber) { this.articleNumber = articleNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
