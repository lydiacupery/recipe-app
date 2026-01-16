package com.lcupery.recipe_app.service;

import java.util.List;

public interface EmbeddingService {

    /**
     * Get embedding vector for a single text string
     * Results are cached to minimize API calls
     */
    List<Double> getEmbedding(String text);

    /**
     * Calculate cosine similarity between two embedding vectors
     * Returns a value between 0.0 (completely different) and 1.0 (identical)
     */
    double cosineSimilarity(List<Double> vector1, List<Double> vector2);

    /**
     * Calculate semantic similarity between two text strings
     * Returns a value between 0.0 (completely different) and 1.0 (identical)
     */
    double calculateSimilarity(String text1, String text2);

    /**
     * Clear the embedding cache
     */
    void clearCache();

    /**
     * Get cache statistics for monitoring
     */
    CacheStats getCacheStats();

    /**
     * Check if the embedding service is available and configured
     */
    boolean isAvailable();

    /**
     * Cache statistics data class
     */
    record CacheStats(int size, int hits, int misses, double hitRate) {}
}
