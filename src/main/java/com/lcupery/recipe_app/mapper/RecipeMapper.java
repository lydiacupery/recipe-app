package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.IngredientDto;
import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.Ingredient;
import com.lcupery.recipe_app.entity.Recipe;

import java.util.stream.Collectors;

public class RecipeMapper {
    public static RecipeDto mapToRecipeDto(Recipe recipe) {
        RecipeDto recipeDto = new RecipeDto();
        recipeDto.setId(recipe.getId());
        recipeDto.setName(recipe.getName());
        recipeDto.setDescription(recipe.getDescription());
        recipeDto.setIngredients(
                recipe.getIngredients().stream()
                        .map(IngredientMapper::mapToIngredientDto)
                        .collect(Collectors.toList())
        );
        return recipeDto;
    }

    public static Recipe mapToRecipe(RecipeDto recipeDto) {
        Recipe recipe = new Recipe();
        recipe.setId(recipeDto.getId());
        recipe.setName(recipeDto.getName());
        recipe.setDescription(recipeDto.getDescription());

        if (recipeDto.getIngredients() != null) {
            recipe.setIngredients(
                    recipeDto.getIngredients().stream()
                            .map(ingredientDto -> {
                                Ingredient ingredient = IngredientMapper.mapToIngredient(ingredientDto);
                                ingredient.setRecipe(recipe);
                                return ingredient;
                            })
                            .collect(Collectors.toList())
            );
        }

        return recipe;
    }
}
