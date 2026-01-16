package com.lcupery.recipe_app.dto;

import com.lcupery.recipe_app.entity.StorageLocation;
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
    private Boolean inPantry;
    private String pantryItemName;
    private StorageLocation pantryLocation;
}
