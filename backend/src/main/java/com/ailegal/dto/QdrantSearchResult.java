package com.ailegal.dto;

/**
 * A single Qdrant search hit: point id (= legal_article.id) and the raw vector similarity score.
 */
public class QdrantSearchResult {

    private long pointId;
    private double score;

    public QdrantSearchResult() {
    }

    public QdrantSearchResult(long pointId, double score) {
        this.pointId = pointId;
        this.score = score;
    }

    public long getPointId() {
        return pointId;
    }

    public void setPointId(long pointId) {
        this.pointId = pointId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}