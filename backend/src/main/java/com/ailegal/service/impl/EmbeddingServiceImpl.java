package com.ailegal.service.impl;

import com.ailegal.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final RestClient restClient;
    private final String model;
    private final int configuredDimensions;

    private volatile int actualDimension = 0;

    public EmbeddingServiceImpl(@Value("${embedding.api-base-url}") String baseUrl,
                                @Value("${embedding.api-key}") String apiKey,
                                @Value("${embedding.model}") String model,
                                @Value("${embedding.dimensions}") int dimensions) {
        this.model = model;
        this.configuredDimensions = dimensions;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text,
                "dimensions", configuredDimensions
        );

        Map<String, Object> response = restClient.post()
                .uri("/embeddings")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("data")) {
            throw new RuntimeException("Embedding API returned empty response");
        }

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
        if (dataList.isEmpty()) {
            throw new RuntimeException("Embedding API returned empty data array");
        }

        List<Double> embeddingList = (List<Double>) dataList.get(0).get("embedding");
        float[] vector = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            vector[i] = embeddingList.get(i).floatValue();
        }

        if (actualDimension == 0) {
            actualDimension = vector.length;
            log.info("Embedding dimension confirmed: {} (configured: {})", actualDimension, configuredDimensions);
            if (actualDimension != configuredDimensions) {
                throw new RuntimeException(String.format(
                        "Embedding dimension mismatch: API returned %d, but configured %d",
                        actualDimension, configuredDimensions));
            }
        }

        return vector;
    }

    @Override
    public int getDimension() {
        if (actualDimension == 0) {
            throw new IllegalStateException("Embedding dimension not confirmed yet. Call embed() first.");
        }
        return actualDimension;
    }
}
