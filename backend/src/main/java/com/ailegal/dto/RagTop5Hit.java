package com.ailegal.dto;

import java.util.List;

/**
 * Final Top-5 hit after vector + keyword + domain weighted ranking.
 * 3A-K: added documentName, articleNumber, matchedConcepts for calibration visibility.
 */
public class RagTop5Hit {

    private Long articleId;
    private String documentName;
    private String articleNumber;
    private double vectorScoreNormalized;
    private double keywordScore;
    private double domainScore;
    private double finalScore;
    private List<String> matchedConcepts;

    public RagTop5Hit() {
    }

    public RagTop5Hit(Long articleId, double vectorScoreNormalized, double keywordScore,
                      double domainScore, double finalScore) {
        this.articleId = articleId;
        this.vectorScoreNormalized = vectorScoreNormalized;
        this.keywordScore = keywordScore;
        this.domainScore = domainScore;
        this.finalScore = finalScore;
    }

    public RagTop5Hit(Long articleId, String documentName, String articleNumber,
                      double vectorScoreNormalized, double keywordScore,
                      double domainScore, double finalScore, List<String> matchedConcepts) {
        this.articleId = articleId;
        this.documentName = documentName;
        this.articleNumber = articleNumber;
        this.vectorScoreNormalized = vectorScoreNormalized;
        this.keywordScore = keywordScore;
        this.domainScore = domainScore;
        this.finalScore = finalScore;
        this.matchedConcepts = matchedConcepts;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public double getVectorScoreNormalized() {
        return vectorScoreNormalized;
    }

    public void setVectorScoreNormalized(double vectorScoreNormalized) {
        this.vectorScoreNormalized = vectorScoreNormalized;
    }

    public double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(double keywordScore) {
        this.keywordScore = keywordScore;
    }

    public double getDomainScore() {
        return domainScore;
    }

    public void setDomainScore(double domainScore) {
        this.domainScore = domainScore;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
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

    public List<String> getMatchedConcepts() {
        return matchedConcepts;
    }

    public void setMatchedConcepts(List<String> matchedConcepts) {
        this.matchedConcepts = matchedConcepts;
    }
}
