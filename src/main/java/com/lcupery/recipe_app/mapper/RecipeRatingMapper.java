package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.RecipeRatingDto;
import com.lcupery.recipe_app.entity.RecipeRating;

public class RecipeRatingMapper {
    public static RecipeRatingDto mapToRecipeRatingDto(RecipeRating rating) {
        RecipeRatingDto dto = new RecipeRatingDto();
        dto.setId(rating.getId());
        dto.setRecipeId(rating.getRecipe().getId());
        dto.setUserId(rating.getUser() != null ? rating.getUser().getAuth0Id() : null);
        dto.setRating(rating.getRating());
        dto.setComment(rating.getComment());
        dto.setRaterName(rating.getRaterName());
        dto.setCreatedAt(rating.getCreatedAt());
        dto.setUpdatedAt(rating.getUpdatedAt());
        return dto;
    }
}
