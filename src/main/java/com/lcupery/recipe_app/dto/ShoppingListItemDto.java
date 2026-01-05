package com.lcupery.recipe_app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingListItemDto {
    private Long id;
    private Long recipeId;
    private String recipeName;
    private String ingredientName;
    private String ingredientQuantity;
    private boolean isChecked;
}
