package com.lcupery.recipe_app.controller;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.service.RecipeExtractorService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin("*")
@AllArgsConstructor
@RestController
@RequestMapping("/api/recipes")
public class RecipeExtractorController {

    private RecipeExtractorService extractorService;

    @PostMapping("/extract")
    public ResponseEntity<RecipeDto> extractRecipe(@RequestBody ExtractRequest request) {
        try {
            RecipeDto recipe = extractorService.extractRecipeFromUrl(request.getUrl());
            return ResponseEntity.ok(recipe);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract recipe from URL: " + e.getMessage(), e);
        }
    }

    public static class ExtractRequest {
        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
