package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeDto;

import java.util.List;

public interface RecipeService {
    RecipeDto createRecipe(RecipeDto recipeDto);

    RecipeDto getRecipeById(Long recipeId);

    List<RecipeDto> getAllRecipes();

    RecipeDto updateRecipe(Long recipeId, RecipeDto updatedRecipe);

    void deleteRecipe(Long recipeId);
}
