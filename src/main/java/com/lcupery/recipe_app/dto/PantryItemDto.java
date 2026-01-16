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
public class PantryItemDto {
    private Long id;
    private String name;
    private StorageLocation location;
}
