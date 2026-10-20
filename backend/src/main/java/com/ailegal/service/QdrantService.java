package com.ailegal.service;

import com.ailegal.dto.QdrantSearchResult;

import java.util.List;
import java.util.Map;

public interface QdrantService {

    void ensureCollection(int dimension);

    boolean collectionExists();

    /**
     * Real vector search on collection 'legal_articles' (Top-N by cosine similarity).
     * Uses the Qdrant Query API (POST /collections/{name}/points/query), which is
     * supported since Qdrant 1.10. Returns point id (= legal_article.id) + raw score.
     * Never falls back to MySQL LIKE or mock results.
     */
    List<QdrantSearchResult> searchPoints(float[] queryVector, int limit);

    void upsertPoint(long pointId, float[] vector, Map<String, Object> payload);

    void upsertPoints(List<Long> pointIds, List<float[]> vectors, List<Map<String, Object>> payloads);

    void deletePoint(long pointId);

    void deletePoints(List<Long> pointIds);

    Map<String, Object> getPoint(long pointId);

    int countPoints();

    List<Map<String, Object>> scrollPoints(int limit);
}
