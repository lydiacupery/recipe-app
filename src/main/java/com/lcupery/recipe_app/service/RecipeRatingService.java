package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeRatingDto;
import com.lcupery.recipe_app.entity.User;

import java.util.List;

public interface RecipeRatingService {
    RecipeRatingDto addOrUpdateRating(Long recipeId, int rating, String comment, String raterName, User user);
    List<RecipeRatingDto> getRatingsForRecipe(Long recipeId, User user);
    void deleteRating(Long ratingId, User user);
}
