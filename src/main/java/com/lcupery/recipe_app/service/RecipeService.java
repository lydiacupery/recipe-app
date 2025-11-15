package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeDto;

import java.util.List;

public interface RecipeService {
    RecipeDto createRecipe(RecipeDto recipeDto, String userId);

    RecipeDto getRecipeById(Long recipeId, String userId);

    List<RecipeDto> getAllRecipes(String userId);

    RecipeDto updateRecipe(Long recipeId, RecipeDto updatedRecipe, String userId);

    void deleteRecipe(Long recipeId, String userId);
}
