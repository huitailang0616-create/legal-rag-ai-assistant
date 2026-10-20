package com.ailegal.service;

public interface EmbeddingService {

    /**
     * Get embedding vector for the given text.
     * @return float array of the embedding vector
     */
    float[] embed(String text);

    /**
     * Get the actual embedding dimension from the first successful API call.
     */
    int getDimension();
}
