package com.ailegal.service.impl;

import com.ailegal.dto.QdrantSearchResult;
import com.ailegal.service.QdrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QdrantServiceImpl implements QdrantService {

    private static final Logger log = LoggerFactory.getLogger(QdrantServiceImpl.class);
    private static final String COLLECTION_NAME = "legal_articles";

    private final RestClient restClient;

    public QdrantServiceImpl(@Value("${qdrant.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean collectionExists() {
        try {
            Map<String, Object> resp = restClient.get()
                    .uri("/collections/" + COLLECTION_NAME)
                    .retrieve()
                    .body(Map.class);
            return resp != null && "green".equals(resp.get("status"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void ensureCollection(int dimension) {
        if (collectionExists()) {
            Map<String, Object> info = restClient.get()
                    .uri("/collections/" + COLLECTION_NAME)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> configMap = (Map<String, Object>) info.get("config");
            Map<String, Object> params = (Map<String, Object>) configMap.get("params");
            Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");
            Map<String, Object> config = vectors;
            Object existingSize = config.get("size");
            Object existingDistance = config.get("distance");

            log.info("Existing collection: size={}, distance={}", existingSize, existingDistance);

            if (!String.valueOf(existingSize).equals(String.valueOf(dimension))) {
                throw new RuntimeException(String.format(
                        "Collection dimension mismatch: existing=%s, expected=%d. Manual intervention required.",
                        existingSize, dimension));
            }
            return;
        }

        Map<String, Object> vectorsConfig = Map.of(
                "size", dimension,
                "distance", "Cosine"
        );
        Map<String, Object> body = Map.of("vectors", vectorsConfig);

        restClient.put()
                .uri("/collections/" + COLLECTION_NAME)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Collection '{}' created: size={}, distance=Cosine", COLLECTION_NAME, dimension);
    }

    @Override
    public void upsertPoint(long pointId, float[] vector, Map<String, Object> payload) {
        Map<String, Object> point = Map.of(
                "id", pointId,
                "vector", (Object) vector,
                "payload", (Object) payload
        );
        Map<String, Object> body = Map.of("points", List.of(point));

        restClient.put()
                .uri("/collections/" + COLLECTION_NAME + "/points")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void upsertPoints(List<Long> pointIds, List<float[]> vectors, List<Map<String, Object>> payloads) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < pointIds.size(); i++) {
            points.add(Map.of(
                    "id", pointIds.get(i),
                    "vector", (Object) vectors.get(i),
                    "payload", (Object) payloads.get(i)
            ));
        }
        Map<String, Object> body = Map.of("points", points);

        restClient.put()
                .uri("/collections/" + COLLECTION_NAME + "/points")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deletePoint(long pointId) {
        Map<String, Object> body = Map.of("points", List.of(pointId));

        restClient.post()
                .uri("/collections/" + COLLECTION_NAME + "/points/delete")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deletePoints(List<Long> pointIds) {
        Map<String, Object> body = Map.of("points", pointIds);

        restClient.post()
                .uri("/collections/" + COLLECTION_NAME + "/points/delete")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QdrantSearchResult> searchPoints(float[] queryVector, int limit) {
        Map<String, Object> body = Map.of(
                "query", (Object) queryVector,
                "limit", limit,
                "with_payload", false,
                "with_vector", false
        );

        Map<String, Object> resp = restClient.post()
                .uri("/collections/" + COLLECTION_NAME + "/points/query")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (resp == null || !resp.containsKey("result")) {
            throw new RuntimeException("Qdrant query API returned no result");
        }

        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");
        if (points == null) {
            return List.of();
        }

        List<QdrantSearchResult> hits = new ArrayList<>();
        for (Map<String, Object> p : points) {
            Object id = p.get("id");
            Object score = p.get("score");
            if (id == null || score == null) {
                throw new RuntimeException("Qdrant query API returned a hit without id/score");
            }
            hits.add(new QdrantSearchResult(((Number) id).longValue(), ((Number) score).doubleValue()));
        }
        return hits;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPoint(long pointId) {
        return restClient.get()
                .uri("/collections/" + COLLECTION_NAME + "/points/" + pointId)
                .retrieve()
                .body(Map.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public int countPoints() {
        Map<String, Object> body = Map.of("exact", true);
        Map<String, Object> resp = restClient.post()
                .uri("/collections/" + COLLECTION_NAME + "/points/count")
                .body(body)
                .retrieve()
                .body(Map.class);
        if (resp != null && resp.containsKey("result")) {
            Map<String, Object> result = (Map<String, Object>) resp.get("result");
            Object count = result.get("count");
            return count instanceof Integer ? (Integer) count : 0;
        }
        return 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> scrollPoints(int limit) {
        Map<String, Object> body = Map.of("limit", limit, "with_payload", true);
        Map<String, Object> resp = restClient.post()
                .uri("/collections/" + COLLECTION_NAME + "/points/scroll")
                .body(body)
                .retrieve()
                .body(Map.class);
        if (resp != null && resp.containsKey("result")) {
            Map<String, Object> result = (Map<String, Object>) resp.get("result");
            return (List<Map<String, Object>>) result.get("points");
        }
        return List.of();
    }
}
