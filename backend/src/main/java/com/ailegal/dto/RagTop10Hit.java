package com.ailegal.dto;

/**
 * Raw Qdrant Top-10 hit: articleId + raw vector similarity score (before any normalization).
 */
public class RagTop10Hit {

    private Long articleId;
    private double rawVectorScore;

    public RagTop10Hit() {
    }

    public RagTop10Hit(Long articleId, double rawVectorScore) {
        this.articleId = articleId;
        this.rawVectorScore = rawVectorScore;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public double getRawVectorScore() {
        return rawVectorScore;
    }

    public void setRawVectorScore(double rawVectorScore) {
        this.rawVectorScore = rawVectorScore;
    }
}
