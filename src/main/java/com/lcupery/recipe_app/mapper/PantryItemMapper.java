package com.lcupery.recipe_app.mapper;

import com.lcupery.recipe_app.dto.PantryItemDto;
import com.lcupery.recipe_app.entity.PantryItem;

public class PantryItemMapper {
    public static PantryItemDto mapToDto(PantryItem item) {
        return new PantryItemDto(
                item.getId(),
                item.getName(),
                item.getLocation()
        );
    }

    public static PantryItem mapToEntity(PantryItemDto dto) {
        PantryItem item = new PantryItem();
        item.setId(dto.getId());
        item.setName(dto.getName());
        item.setLocation(dto.getLocation());
        // User is set in the service layer
        return item;
    }
}
