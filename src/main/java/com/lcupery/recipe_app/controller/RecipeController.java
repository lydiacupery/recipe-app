package com.lcupery.recipe_app.controller;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.service.ImageGenerationService;
import com.lcupery.recipe_app.service.ImageRecipeExtractorService;
import com.lcupery.recipe_app.service.RecipeService;
import com.lcupery.recipe_app.service.UserService;
import com.lcupery.recipe_app.service.VercelBlobService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private UserService userService;
    private VercelBlobService vercelBlobService;
    private ImageGenerationService imageGenerationService;
    private ImageRecipeExtractorService imageRecipeExtractorService;

    // build add recipe REST API
    @PostMapping
    public ResponseEntity<RecipeDto> createRecipe(@Valid @RequestBody RecipeDto recipeDto, Authentication authentication) {
        User user = getUserFromJwt(authentication);
        RecipeDto savedRecipe = recipeService.createRecipe(recipeDto, user);
        return new ResponseEntity<>(savedRecipe, HttpStatus.CREATED);
    }

    // get recipe REST API
    @GetMapping("{id}")
    public ResponseEntity<RecipeDto> getRecipeById(@PathVariable("id") Long recipeId, Authentication authentication) {
        User user = getUserFromJwt(authentication);
        RecipeDto recipeDto = recipeService.getRecipeById(recipeId, user);
        return ResponseEntity.ok(recipeDto);
    }

    @GetMapping
    public ResponseEntity<List<RecipeDto>> getAllRecipes(Authentication authentication) {
        User user = getUserFromJwt(authentication);
        List<RecipeDto> recipes = recipeService.getAllRecipes(user);
        return ResponseEntity.ok(recipes);
    }

    // Build Update Recipe REST API
    @PutMapping("{id}")
    public ResponseEntity<RecipeDto> updateRecipe(@PathVariable("id") Long recipeId, @Valid @RequestBody RecipeDto updatedRecipe, Authentication authentication) {
        User user = getUserFromJwt(authentication);
        RecipeDto recipeDto = recipeService.updateRecipe(recipeId, updatedRecipe, user);
        return ResponseEntity.ok(recipeDto);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<String> deleteRecipe(@PathVariable("id") Long recipeId, Authentication authentication) {
        User user = getUserFromJwt(authentication);
        recipeService.deleteRecipe(recipeId, user);
        return ResponseEntity.ok("Recipe deleted");
    }

    // Get current user info from JWT token
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getCurrentUser(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        log.info("Current user - ID: {}, Email: {}, Name: {}", userId, email, name);

        return ResponseEntity.ok(Map.of(
            "userId", userId != null ? userId : "",
            "email", email != null ? email : "",
            "name", name != null ? name : ""
        ));
    }

    // Helper method to get or create user from JWT token
    private User getUserFromJwt(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String auth0Id = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        log.info("Processing request for user - auth0_id: {}, email: {}", auth0Id, email);

        return userService.findOrCreateUser(auth0Id, email, name);
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
