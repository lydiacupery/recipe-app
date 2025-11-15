package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.RecipeShare;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.mapper.RecipeMapper;
import com.lcupery.recipe_app.repository.RecipeRepository;
import com.lcupery.recipe_app.repository.RecipeShareRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class RecipeShareServiceImpl implements RecipeShareService {

    private RecipeRepository recipeRepository;
    private RecipeShareRepository recipeShareRepository;

    @Override
    @Transactional
    public String createShareLink(Long recipeId, User user) {
        // Get recipe and verify ownership
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found or you don't have access"));

        // Check if share already exists
        RecipeShare existingShare = recipeShareRepository.findByRecipe(recipe).orElse(null);

        if (existingShare != null) {
            log.info("Returning existing share token for recipe {}", recipeId);
            return existingShare.getToken();
        }

        // Create new share
        String token = UUID.randomUUID().toString();
        RecipeShare share = new RecipeShare();
        share.setToken(token);
        share.setRecipe(recipe);

        recipeShareRepository.save(share);
        log.info("Created share link for recipe {} with token {}", recipeId, token);

        return token;
    }

    @Override
    @Transactional
    public RecipeDto getSharedRecipe(String token) {
        RecipeShare share = recipeShareRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or expired"));

        // Increment view count
        share.setViewCount(share.getViewCount() + 1);
        recipeShareRepository.save(share);

        log.info("Shared recipe viewed - token: {}, views: {}", token, share.getViewCount());

        return RecipeMapper.mapToRecipeDto(share.getRecipe());
    }

    @Override
    @Transactional
    public RecipeDto saveSharedRecipe(String token, User user) {
        RecipeShare share = recipeShareRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or expired"));

        Recipe originalRecipe = share.getRecipe();

        // Create a copy of the recipe for the current user
        Recipe copiedRecipe = new Recipe();
        copiedRecipe.setUser(user);
        copiedRecipe.setName(originalRecipe.getName());
        copiedRecipe.setDescription(originalRecipe.getDescription());
        copiedRecipe.setSourceType(originalRecipe.getSourceType());
        copiedRecipe.setSourceValue(originalRecipe.getSourceValue());
        copiedRecipe.setPrepTime(originalRecipe.getPrepTime());
        copiedRecipe.setCookTime(originalRecipe.getCookTime());
        copiedRecipe.setServings(originalRecipe.getServings());
        copiedRecipe.setCategory(originalRecipe.getCategory());
        copiedRecipe.setImageUrl(originalRecipe.getImageUrl());

        // Set attribution
        copiedRecipe.setCopy(true);
        copiedRecipe.setOriginalAuthorName(originalRecipe.getUser().getName());
        copiedRecipe.setOriginalAuthorId(originalRecipe.getUser().getId());

        // Copy ingredients
        originalRecipe.getIngredients().forEach(ingredient -> {
            com.lcupery.recipe_app.entity.Ingredient copiedIngredient = new com.lcupery.recipe_app.entity.Ingredient();
            copiedIngredient.setName(ingredient.getName());
            copiedIngredient.setQuantity(ingredient.getQuantity());
            copiedIngredient.setRecipe(copiedRecipe);
            copiedRecipe.getIngredients().add(copiedIngredient);
        });

        // Copy steps
        originalRecipe.getSteps().forEach(step -> {
            com.lcupery.recipe_app.entity.Step copiedStep = new com.lcupery.recipe_app.entity.Step();
            copiedStep.setStepNumber(step.getStepNumber());
            copiedStep.setInstruction(step.getInstruction());
            copiedStep.setRecipe(copiedRecipe);
            copiedRecipe.getSteps().add(copiedStep);
        });

        Recipe savedRecipe = recipeRepository.save(copiedRecipe);

        // Increment save count
        share.setSaveCount(share.getSaveCount() + 1);
        recipeShareRepository.save(share);

        log.info("Recipe saved from share - token: {}, saves: {}, new recipe id: {}",
                 token, share.getSaveCount(), savedRecipe.getId());

        return RecipeMapper.mapToRecipeDto(savedRecipe);
    }
}
