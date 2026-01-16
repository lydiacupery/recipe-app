package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.ShoppingListItemDto;
import com.lcupery.recipe_app.entity.PantryItem;
import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.ShoppingListItem;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.mapper.ShoppingListItemMapper;
import com.lcupery.recipe_app.repository.PantryItemRepository;
import com.lcupery.recipe_app.repository.RecipeRepository;
import com.lcupery.recipe_app.repository.ShoppingListItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShoppingListServiceImpl implements ShoppingListService {

    private final ShoppingListItemRepository shoppingListItemRepository;
    private final RecipeRepository recipeRepository;
    private final PantryItemRepository pantryItemRepository;
    private final EmbeddingService embeddingService;

    @Value("${matching.strategy:both}")
    private String matchingStrategy;

    @Value("${matching.semantic.threshold:0.75}")
    private double semanticThreshold;

    @Value("${matching.local.threshold:0.60}")
    private double localThreshold;

    public ShoppingListServiceImpl(
            ShoppingListItemRepository shoppingListItemRepository,
            RecipeRepository recipeRepository,
            PantryItemRepository pantryItemRepository,
            EmbeddingService embeddingService) {
        this.shoppingListItemRepository = shoppingListItemRepository;
        this.recipeRepository = recipeRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    @Transactional
    public List<ShoppingListItemDto> addRecipeToShoppingList(Long recipeId, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        // Check if recipe already in shopping list
        List<ShoppingListItem> existing = shoppingListItemRepository.findByUserAndRecipe(user, recipe);
        if (!existing.isEmpty()) {
            throw new IllegalStateException("Recipe already in shopping list");
        }

        // Get user's pantry items for auto-checking
        List<PantryItem> pantryItems = pantryItemRepository.findByUserOrderByNameAsc(user);

        // Add all ingredients from recipe
        List<ShoppingListItem> items = recipe.getIngredients().stream()
                .map(ingredient -> {
                    ShoppingListItem item = new ShoppingListItem();
                    item.setUser(user);
                    item.setRecipe(recipe);
                    item.setIngredientName(ingredient.getName());
                    item.setIngredientQuantity(ingredient.getQuantity());

                    // Auto-check if ingredient matches any pantry item
                    boolean inPantry = pantryItems.stream()
                            .anyMatch(pantryItem -> fuzzyMatch(ingredient.getName(), pantryItem.getName()));
                    item.setChecked(inPantry);

                    if (inPantry) {
                        log.debug("Auto-checked ingredient '{}' (found in pantry)", ingredient.getName());
                    }

                    return item;
                })
                .collect(Collectors.toList());

        List<ShoppingListItem> saved = shoppingListItemRepository.saveAll(items);
        log.info("Added {} ingredients from recipe {} to shopping list for user {} ({} auto-checked)",
                saved.size(), recipeId, user.getAuth0Id(),
                saved.stream().filter(ShoppingListItem::isChecked).count());

        return saved.stream()
                .map(ShoppingListItemMapper::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeRecipeFromShoppingList(Long recipeId, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        shoppingListItemRepository.deleteByUserAndRecipe(user, recipe);
        log.info("Removed all items from recipe {} for user {}", recipeId, user.getAuth0Id());
    }

    @Override
    public List<ShoppingListItemDto> getShoppingList(User user) {
        List<ShoppingListItem> items = shoppingListItemRepository.findByUserOrderByIngredientNameAsc(user);
        List<PantryItem> pantryItems = pantryItemRepository.findByUserOrderByNameAsc(user);

        return items.stream()
                .map(item -> {
                    ShoppingListItemDto dto = ShoppingListItemMapper.mapToDto(item);

                    // Check if this ingredient matches any pantry items (fuzzy match)
                    for (PantryItem pantryItem : pantryItems) {
                        if (fuzzyMatch(item.getIngredientName(), pantryItem.getName())) {
                            dto.setInPantry(true);
                            dto.setPantryItemName(pantryItem.getName());
                            dto.setPantryLocation(pantryItem.getLocation());
                            break;
                        }
                    }

                    // Set defaults if not found in pantry
                    if (dto.getInPantry() == null) {
                        dto.setInPantry(false);
                        dto.setPantryItemName(null);
                        dto.setPantryLocation(null);
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Fuzzy match between shopping list ingredient and pantry item
     * Supports three strategies:
     * 1. "semantic" - OpenAI embeddings only (with fallback to local on error)
     * 2. "local" - Word + character similarity only
     * 3. "both" - Try BOTH semantic AND local, match if EITHER succeeds
     */
    private boolean fuzzyMatch(String shoppingListItem, String pantryItem) {
        if (shoppingListItem == null || pantryItem == null) {
            return false;
        }

        String normalized1 = shoppingListItem.toLowerCase().trim();
        String normalized2 = pantryItem.toLowerCase().trim();

        // Exact match
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // Strategy: BOTH (semantic OR local)
        if ("both".equalsIgnoreCase(matchingStrategy)) {
            boolean semanticMatch = false;
            boolean localMatch = false;

            // Try semantic matching
            try {
                if (embeddingService.isAvailable()) {
                    double similarity = embeddingService.calculateSimilarity(normalized1, normalized2);
                    semanticMatch = similarity >= semanticThreshold;
                    log.debug("Semantic match '{}' vs '{}': similarity={}, threshold={}, match={}",
                            normalized1, normalized2, similarity, semanticThreshold, semanticMatch);
                }
            } catch (Exception e) {
                log.warn("Error during semantic matching in BOTH mode", e);
            }

            // Try local matching
            localMatch = localFuzzyMatch(normalized1, normalized2);

            // Match if EITHER method matched
            boolean finalMatch = semanticMatch || localMatch;
            if (finalMatch) {
                log.debug("BOTH strategy '{}' vs '{}': semantic={}, local={}, FINAL=MATCH",
                        normalized1, normalized2, semanticMatch, localMatch);
            }
            return finalMatch;
        }

        // Strategy: SEMANTIC only (with fallback)
        if ("semantic".equalsIgnoreCase(matchingStrategy)) {
            try {
                if (embeddingService.isAvailable()) {
                    double similarity = embeddingService.calculateSimilarity(normalized1, normalized2);
                    boolean isMatch = similarity >= semanticThreshold;

                    log.debug("Semantic match '{}' vs '{}': similarity={}, threshold={}, match={}",
                            normalized1, normalized2, similarity, semanticThreshold, isMatch);

                    return isMatch;
                } else {
                    log.warn("Semantic matching requested but EmbeddingService not available. Falling back to local matching.");
                }
            } catch (Exception e) {
                log.error("Error during semantic matching, falling back to local matching", e);
            }
        }

        // Strategy: LOCAL only (or fallback from semantic)
        return localFuzzyMatch(normalized1, normalized2);
    }

    /**
     * Local fuzzy matching using combined word-level and character-level similarity
     * Uses both word-level Jaccard similarity and character-level n-gram cosine similarity
     * Special handling for ingredient modifiers (e.g., "milk" matching "warm whole milk")
     */
    private boolean localFuzzyMatch(String normalized1, String normalized2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(normalized1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(normalized2.split("\\s+")));

        // Check if at least one word matches exactly
        Set<String> wordIntersection = new HashSet<>(words1);
        wordIntersection.retainAll(words2);

        if (wordIntersection.isEmpty()) {
            // No exact word match - reject to prevent "black pepper" matching "bell pepper"
            log.debug("Local match '{}' vs '{}': NO exact word overlap, rejecting", normalized1, normalized2);
            return false;
        }

        // Check if one ingredient is a complete word subset of the other (handles modifiers)
        // e.g., "milk" (1 word) is subset of "warm whole milk" (3 words)
        // But avoid matching "pepper" (1 word) with "bell pepper" (2 words) - need bigger difference
        int wordCount1 = words1.size();
        int wordCount2 = words2.size();
        int wordCountDiff = Math.abs(wordCount1 - wordCount2);

        boolean isSubset = words1.containsAll(words2) || words2.containsAll(words1);

        if (isSubset && wordCountDiff >= 2) {
            // One contains all words from the other AND significant word count difference
            // Likely the same ingredient with multiple modifiers (e.g., "warm whole milk" vs "milk")
            log.debug("Local match '{}' vs '{}': WORD SUBSET detected (diff={}), auto-matching",
                    normalized1, normalized2, wordCountDiff);
            return true;
        } else if (isSubset) {
            log.debug("Local match '{}' vs '{}': Word subset but small diff ({}), checking similarity",
                    normalized1, normalized2, wordCountDiff);
        }

        // Calculate word-level Jaccard similarity (how many words overlap)
        double wordSimilarity = calculateWordSimilarity(normalized1, normalized2);

        // Calculate character-level cosine similarity (character n-gram overlap)
        double charSimilarity = calculateCharSimilarity(normalized1, normalized2);

        // Weighted combination: 60% word similarity, 40% character similarity
        double combinedSimilarity = (wordSimilarity * 0.6) + (charSimilarity * 0.4);

        boolean isMatch = combinedSimilarity >= localThreshold;

        log.debug("Local match '{}' vs '{}': word={}, char={}, combined={}, threshold={}, match={}",
                normalized1, normalized2, wordSimilarity, charSimilarity, combinedSimilarity, localThreshold, isMatch);

        return isMatch;
    }

    /**
     * Calculate word-level Jaccard similarity
     * Returns the ratio of common words to total unique words
     */
    private double calculateWordSimilarity(String s1, String s2) {
        Set<String> set1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Calculate character-level cosine similarity using 2-character n-grams
     * Returns cosine similarity between n-gram frequency vectors
     */
    private double calculateCharSimilarity(String s1, String s2) {
        Map<String, Integer> vector1 = getNGrams(s1, 2);
        Map<String, Integer> vector2 = getNGrams(s2, 2);

        // Calculate dot product
        double dotProduct = 0.0;
        for (String key : vector1.keySet()) {
            if (vector2.containsKey(key)) {
                dotProduct += vector1.get(key) * vector2.get(key);
            }
        }

        // Calculate magnitudes
        double magnitude1 = Math.sqrt(vector1.values().stream().mapToDouble(v -> v * v).sum());
        double magnitude2 = Math.sqrt(vector2.values().stream().mapToDouble(v -> v * v).sum());

        if (magnitude1 == 0 || magnitude2 == 0) {
            return 0.0;
        }

        return dotProduct / (magnitude1 * magnitude2);
    }

    /**
     * Generate n-grams (substrings of length n) from a string
     * Returns a frequency map of n-grams
     */
    private Map<String, Integer> getNGrams(String str, int n) {
        Map<String, Integer> ngrams = new HashMap<>();
        for (int i = 0; i <= str.length() - n; i++) {
            String ngram = str.substring(i, i + n);
            ngrams.put(ngram, ngrams.getOrDefault(ngram, 0) + 1);
        }
        return ngrams;
    }

    @Override
    @Transactional
    public ShoppingListItemDto toggleItemChecked(Long itemId, User user) {
        ShoppingListItem item = shoppingListItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shopping list item not found: " + itemId));

        // Verify ownership
        if (!item.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access to shopping list item");
        }

        item.setChecked(!item.isChecked());
        ShoppingListItem saved = shoppingListItemRepository.save(item);
        log.debug("Toggled item {} checked status to {} for user {}",
                itemId, saved.isChecked(), user.getAuth0Id());

        return ShoppingListItemMapper.mapToDto(saved);
    }

    @Override
    @Transactional
    public void clearCheckedItems(User user) {
        List<ShoppingListItem> checkedItems = shoppingListItemRepository
                .findByUserOrderByIngredientNameAsc(user)
                .stream()
                .filter(ShoppingListItem::isChecked)
                .collect(Collectors.toList());

        shoppingListItemRepository.deleteAll(checkedItems);
        log.info("Cleared {} checked items for user {}", checkedItems.size(), user.getAuth0Id());
    }

    @Override
    @Transactional
    public void clearShoppingList(User user) {
        List<ShoppingListItem> allItems = shoppingListItemRepository.findByUserOrderByIngredientNameAsc(user);
        shoppingListItemRepository.deleteAll(allItems);
        log.info("Cleared entire shopping list ({} items) for user {}", allItems.size(), user.getAuth0Id());
    }
}
