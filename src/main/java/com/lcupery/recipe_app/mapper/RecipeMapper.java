package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.IngredientDto;
import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.dto.StepDto;
import com.lcupery.recipe_app.entity.Ingredient;
import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.Step;

import java.util.stream.Collectors;

public class RecipeMapper {
    public static RecipeDto mapToRecipeDto(Recipe recipe) {
        RecipeDto recipeDto = new RecipeDto();
        recipeDto.setId(recipe.getId());
        recipeDto.setUserId(recipe.getUser() != null ? recipe.getUser().getAuth0Id() : null);
        recipeDto.setName(recipe.getName());
        recipeDto.setDescription(recipe.getDescription());
        recipeDto.setSourceType(recipe.getSourceType());
        recipeDto.setSourceValue(recipe.getSourceValue());
        recipeDto.setPrepTime(recipe.getPrepTime());
        recipeDto.setCookTime(recipe.getCookTime());
        recipeDto.setServings(recipe.getServings());
        recipeDto.setCategory(recipe.getCategory());
        recipeDto.setImageUrl(recipe.getImageUrl());
        recipeDto.setCopy(recipe.isCopy());
        recipeDto.setOriginalAuthorName(recipe.getOriginalAuthorName());
        recipeDto.setOriginalAuthorId(recipe.getOriginalAuthorId());
        recipeDto.setIngredients(
                recipe.getIngredients().stream()
                        .map(IngredientMapper::mapToIngredientDto)
                        .collect(Collectors.toList())
        );
        recipeDto.setSteps(
                recipe.getSteps().stream()
                        .map(StepMapper::mapToStepDto)
                        .collect(Collectors.toList())
        );
        return recipeDto;
    }

    public static Recipe mapToRecipe(RecipeDto recipeDto) {
        Recipe recipe = new Recipe();
        recipe.setId(recipeDto.getId());
        // User is set in the service layer, not from DTO
        recipe.setName(recipeDto.getName());
        recipe.setDescription(recipeDto.getDescription());
        recipe.setSourceType(recipeDto.getSourceType());
        recipe.setSourceValue(recipeDto.getSourceValue());
        recipe.setPrepTime(recipeDto.getPrepTime());
        recipe.setCookTime(recipeDto.getCookTime());
        recipe.setServings(recipeDto.getServings());
        recipe.setCategory(recipeDto.getCategory());
        recipe.setImageUrl(recipeDto.getImageUrl());
        recipe.setCopy(recipeDto.isCopy());
        recipe.setOriginalAuthorName(recipeDto.getOriginalAuthorName());
        recipe.setOriginalAuthorId(recipeDto.getOriginalAuthorId());

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

        if (recipeDto.getSteps() != null) {
            recipe.setSteps(
                    recipeDto.getSteps().stream()
                            .map(stepDto -> {
                                Step step = StepMapper.mapToStep(stepDto);
                                step.setRecipe(recipe);
                                return step;
                            })
                            .collect(Collectors.toList())
            );
        }

        return recipe;
    }
}
