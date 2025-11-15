package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.User;

public interface RecipeShareService {
    String createShareLink(Long recipeId, User user);
    RecipeDto getSharedRecipe(String token);
    RecipeDto saveSharedRecipe(String token, User user);
}
