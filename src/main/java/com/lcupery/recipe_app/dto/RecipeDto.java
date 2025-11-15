package com.lcupery.recipe_app.dto;

import com.lcupery.recipe_app.entity.SourceType;
import com.lcupery.recipe_app.validation.ValidRecipeSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ValidRecipeSource
public class RecipeDto {
    private Long id;
    private String userId;
    private String name;
    private String description;
    private SourceType sourceType;
    private String sourceValue;
    private String prepTime;
    private String cookTime;
    private String servings;
    private String category;
    private String imageUrl;
    private List<IngredientDto> ingredients = new ArrayList<>();
    private List<StepDto> steps = new ArrayList<>();
}
