package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeRatingDto;
import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.RecipeRating;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.mapper.RecipeRatingMapper;
import com.lcupery.recipe_app.repository.RecipeRatingRepository;
import com.lcupery.recipe_app.repository.RecipeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class RecipeRatingServiceImpl implements RecipeRatingService {

    private RecipeRepository recipeRepository;
    private RecipeRatingRepository ratingRepository;

    @Override
    @Transactional
    public RecipeRatingDto addOrUpdateRating(Long recipeId, int rating, String comment, String raterName, User user) {
        // Verify user owns this recipe
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found or you don't have access"));

        // Validate rating
        if (rating < 1 || rating > 10) {
            throw new IllegalArgumentException("Rating must be between 1 and 10");
        }

        // Always create a new rating (supports multiple ratings per user for different family members)
        RecipeRating recipeRating = new RecipeRating();
        recipeRating.setRecipe(recipe);
        recipeRating.setUser(user);
        recipeRating.setRating(rating);
        recipeRating.setComment(comment);
        recipeRating.setRaterName(raterName != null && !raterName.trim().isEmpty() ? raterName : "Me");

        RecipeRating savedRating = ratingRepository.save(recipeRating);
        log.info("Rating saved for recipe {} by user {} - rater: {}, rating: {}/10",
                 recipeId, user.getAuth0Id(), raterName, rating);

        return RecipeRatingMapper.mapToRecipeRatingDto(savedRating);
    }

    @Override
    public List<RecipeRatingDto> getRatingsForRecipe(Long recipeId, User user) {
        // Verify user owns this recipe
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found or you don't have access"));

        List<RecipeRating> ratings = ratingRepository.findByRecipeOrderByCreatedAtDesc(recipe);

        return ratings.stream()
                .map(RecipeRatingMapper::mapToRecipeRatingDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteRating(Long ratingId, User user) {
        // Use fetch join to eagerly load recipe and user to avoid LazyInitializationException
        RecipeRating rating = ratingRepository.findByIdWithRecipeAndUser(ratingId)
                .orElseThrow(() -> new ResourceNotFoundException("Rating not found"));

        // Verify user owns the recipe this rating belongs to
        if (!rating.getRecipe().getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("You don't have access to delete this rating");
        }

        ratingRepository.delete(rating);
        log.info("Rating {} deleted by user {}", ratingId, user.getAuth0Id());
    }
}
