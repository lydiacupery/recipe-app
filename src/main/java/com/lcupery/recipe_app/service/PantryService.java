package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.PantryItemDto;
import com.lcupery.recipe_app.entity.User;

import java.util.List;

public interface PantryService {
    // Get all pantry items for user
    List<PantryItemDto> getAllPantryItems(User user);

    // Add item to pantry
    PantryItemDto addPantryItem(PantryItemDto pantryItemDto, User user);

    // Update item (change location)
    PantryItemDto updatePantryItem(Long itemId, PantryItemDto pantryItemDto, User user);

    // Delete item from pantry
    void deletePantryItem(Long itemId, User user);
}
