package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.User;

import java.util.List;

public interface RecipeService {
    RecipeDto createRecipe(RecipeDto recipeDto, User user);

    RecipeDto getRecipeById(Long recipeId, User user);

    List<RecipeDto> getAllRecipes(User user);

    RecipeDto updateRecipe(Long recipeId, RecipeDto updatedRecipe, User user);

    void deleteRecipe(Long recipeId, User user);
}
