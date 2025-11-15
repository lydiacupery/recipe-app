package com.lcupery.recipe_app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRatingDto {
    private Long id;
    private Long recipeId;
    private String userId;
    private int rating; // 1-10 scale
    private String comment;
    private String raterName; // e.g., "Me", "John", "My husband"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
