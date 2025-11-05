package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.IngredientDto;
import com.lcupery.recipe_app.entity.Ingredient;

public class IngredientMapper {
    public static IngredientDto mapToIngredientDto(Ingredient ingredient) {
        return new IngredientDto(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getQuantity()
        );
    }

    public static Ingredient mapToIngredient(IngredientDto ingredientDto) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(ingredientDto.getId());
        ingredient.setName(ingredientDto.getName());
        ingredient.setQuantity(ingredientDto.getQuantity());
        return ingredient;
    }
}
