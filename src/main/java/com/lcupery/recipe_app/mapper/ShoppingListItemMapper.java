package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.ShoppingListItemDto;
import com.lcupery.recipe_app.entity.ShoppingListItem;

public class ShoppingListItemMapper {
    public static ShoppingListItemDto mapToDto(ShoppingListItem item) {
        return new ShoppingListItemDto(
                item.getId(),
                item.getRecipe().getId(),
                item.getRecipe().getName(),
                item.getIngredientName(),
                item.getIngredientQuantity(),
                item.isChecked()
        );
    }

    public static ShoppingListItem mapToEntity(ShoppingListItemDto dto) {
        ShoppingListItem item = new ShoppingListItem();
        item.setId(dto.getId());
        item.setIngredientName(dto.getIngredientName());
        item.setIngredientQuantity(dto.getIngredientQuantity());
        item.setChecked(dto.isChecked());
        // User and Recipe are set in the service layer
        return item;
    }
}
