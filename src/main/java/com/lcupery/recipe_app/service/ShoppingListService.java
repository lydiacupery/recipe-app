package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.ShoppingListItemDto;
import com.lcupery.recipe_app.entity.User;

import java.util.List;

public interface ShoppingListService {
    // Add entire recipe to shopping list
    List<ShoppingListItemDto> addRecipeToShoppingList(Long recipeId, User user);

    // Remove all items from a specific recipe
    void removeRecipeFromShoppingList(Long recipeId, User user);

    // Get all shopping list items for user
    List<ShoppingListItemDto> getShoppingList(User user);

    // Toggle checked status of an item
    ShoppingListItemDto toggleItemChecked(Long itemId, User user);

    // Clear all checked items
    void clearCheckedItems(User user);

    // Clear entire shopping list
    void clearShoppingList(User user);
}
