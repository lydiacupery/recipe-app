package com.lcupery.recipe_app.controller;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.service.ImageGenerationService;
import com.lcupery.recipe_app.service.ImageRecipeExtractorService;
import com.lcupery.recipe_app.service.RecipeService;
import com.lcupery.recipe_app.service.VercelBlobService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin("*")
@AllArgsConstructor
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private RecipeService recipeService;
    private VercelBlobService vercelBlobService;
    private ImageGenerationService imageGenerationService;
    private ImageRecipeExtractorService imageRecipeExtractorService;

    // build add recipe REST API
    @PostMapping
    public ResponseEntity<RecipeDto> createRecipe(@Valid @RequestBody RecipeDto recipeDto) {
        RecipeDto savedRecipe = recipeService.createRecipe(recipeDto);
        return new ResponseEntity<>(savedRecipe, HttpStatus.CREATED);
    }

    // get recipe REST API
    @GetMapping("{id}")
    public ResponseEntity<RecipeDto> getRecipeById(@PathVariable("id") Long recipeId) {
        RecipeDto recipeDto = recipeService.getRecipeById(recipeId);
        return ResponseEntity.ok(recipeDto);
    }

    @GetMapping
    public ResponseEntity<List<RecipeDto>> getAllRecipes() {
        List<RecipeDto> recipes = recipeService.getAllRecipes();
        return ResponseEntity.ok(recipes);
    }

    // Build Update Recipe REST API
    @PutMapping("{id}")
    public ResponseEntity<RecipeDto> updateRecipe(@PathVariable("id") Long recipeId, @Valid @RequestBody RecipeDto updatedRecipe) {
        log.debug("DEBUG - Received update request for recipe ID: {}", recipeId);
        log.debug("DEBUG - Category value in request: {}", updatedRecipe.getCategory());
        log.debug("DEBUG - Full RecipeDto: {}", updatedRecipe);
        RecipeDto recipeDto = recipeService.updateRecipe(recipeId, updatedRecipe);
        log.debug("DEBUG - Category value after update: {}", recipeDto.getCategory());
        return ResponseEntity.ok(recipeDto);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<String> deleteRecipe(@PathVariable("id") Long recipeId) {
        recipeService.deleteRecipe(recipeId);
        return ResponseEntity.ok("Recipe deleted");
    }

    @PostMapping(value = "/upload-image", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = vercelBlobService.uploadImage(file);
            return ResponseEntity.ok(Map.of("url", imageUrl));
        } catch (Exception e) {
            log.error("Error uploading image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate-image")
    public ResponseEntity<Map<String, String>> generateImage(@RequestBody Map<String, String> request) {
        try {
            String recipeName = request.get("name");
            String description = request.get("description");
            String ingredients = request.get("ingredients");
            String steps = request.get("steps");

            if (recipeName == null || recipeName.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Recipe name is required"));
            }

            log.info("Generating image for recipe: {}", recipeName);

            String imageUrl = imageGenerationService.generateRecipeImage(recipeName, description, ingredients, steps);
            return ResponseEntity.ok(Map.of("url", imageUrl));
        } catch (Exception e) {
            log.error("Error generating image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/extract-from-image", consumes = "multipart/form-data")
    public ResponseEntity<?> extractFromImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "citation", required = false) String citation) {
        try {
            log.info("Extracting recipe from uploaded image: {}", file.getOriginalFilename());

            RecipeDto recipe = imageRecipeExtractorService.extractRecipeFromImage(file, citation);
            return ResponseEntity.ok(recipe);
        } catch (Exception e) {
            log.error("Error extracting recipe from image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
