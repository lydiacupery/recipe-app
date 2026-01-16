package com.lcupery.recipe_app.service;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final OpenAiService openAiService;
    private final Map<String, List<Double>> embeddingCache;
    private final AtomicInteger cacheHits;
    private final AtomicInteger cacheMisses;
    private final String embeddingModel;

    public EmbeddingServiceImpl(
            @Value("${openai.api.key:#{null}}") String apiKey,
            @Value("${openai.embedding.model:text-embedding-3-small}") String embeddingModel) {

        this.embeddingModel = embeddingModel;
        this.embeddingCache = new ConcurrentHashMap<>();
        this.cacheHits = new AtomicInteger(0);
        this.cacheMisses = new AtomicInteger(0);

        if (apiKey != null && !apiKey.isEmpty()) {
            this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(30));
            log.info("OpenAI EmbeddingService initialized with model: {}", embeddingModel);
        } else {
            this.openAiService = null;
            log.warn("OpenAI API key not configured. EmbeddingService will not be available.");
        }
    }

    @Override
    public List<Double> getEmbedding(String text) {
        if (openAiService == null) {
            throw new IllegalStateException("OpenAI service not initialized. Please configure openai.api.key");
        }

        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Normalize the text for caching (lowercase, trim)
        String normalizedText = text.toLowerCase().trim();

        // Check cache first
        if (embeddingCache.containsKey(normalizedText)) {
            cacheHits.incrementAndGet();
            log.debug("Cache hit for text: {}", normalizedText);
            return embeddingCache.get(normalizedText);
        }

        cacheMisses.incrementAndGet();
        log.debug("Cache miss for text: {}", normalizedText);

        try {
            // Call OpenAI API
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(embeddingModel)
                    .input(Collections.singletonList(normalizedText))
                    .build();

            List<Embedding> embeddings = openAiService.createEmbeddings(request).getData();

            if (embeddings.isEmpty()) {
                log.error("No embeddings returned from OpenAI for text: {}", normalizedText);
                return Collections.emptyList();
            }

            List<Double> embedding = embeddings.get(0).getEmbedding();

            // Cache the result
            embeddingCache.put(normalizedText, embedding);
            log.debug("Cached embedding for text: {} (cache size: {})", normalizedText, embeddingCache.size());

            return embedding;

        } catch (Exception e) {
            log.error("Error getting embedding from OpenAI for text: {}", normalizedText, e);
            throw new RuntimeException("Failed to get embedding from OpenAI", e);
        }
    }

    @Override
    public double cosineSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1 == null || vector2 == null || vector1.isEmpty() || vector2.isEmpty()) {
            return 0.0;
        }

        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;

        for (int i = 0; i < vector1.size(); i++) {
            double val1 = vector1.get(i);
            double val2 = vector2.get(i);

            dotProduct += val1 * val2;
            magnitude1 += val1 * val1;
            magnitude2 += val2 * val2;
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (magnitude1 * magnitude2);
    }

    @Override
    public double calculateSimilarity(String text1, String text2) {
        try {
            List<Double> embedding1 = getEmbedding(text1);
            List<Double> embedding2 = getEmbedding(text2);
            return cosineSimilarity(embedding1, embedding2);
        } catch (Exception e) {
            log.error("Error calculating similarity between '{}' and '{}'", text1, text2, e);
            return 0.0;
        }
    }

    @Override
    public void clearCache() {
        int previousSize = embeddingCache.size();
        embeddingCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        log.info("Embedding cache cleared. Previous size: {}", previousSize);
    }

    @Override
    public CacheStats getCacheStats() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;

        return new CacheStats(embeddingCache.size(), hits, misses, hitRate);
    }

    public boolean isAvailable() {
        return openAiService != null;
    }
}
