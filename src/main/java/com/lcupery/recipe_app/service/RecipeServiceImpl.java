package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.mapper.RecipeMapper;
import com.lcupery.recipe_app.repository.RecipeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private RecipeRepository recipeRepository;

    @Override
    public RecipeDto createRecipe(RecipeDto recipeDto, User user) {
        Recipe recipe = RecipeMapper.mapToRecipe(recipeDto);
        recipe.setUser(user);
        Recipe savedRecipe = recipeRepository.save(recipe);
        return RecipeMapper.mapToRecipeDto(savedRecipe);
    }

    @Override
    public RecipeDto getRecipeById(Long recipeId, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe does not exist with given id: " + recipeId));
        return RecipeMapper.mapToRecipeDto(recipe);
    }

    @Override
    public List<RecipeDto> getAllRecipes(User user) {
        List<Recipe> recipes = recipeRepository.findByUser(user);
        return recipes.stream()
                .map(RecipeMapper::mapToRecipeDto)
                .collect(Collectors.toList());
    }

    @Override
    public RecipeDto updateRecipe(Long recipeId, RecipeDto updatedRecipe, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe does not exist with id: " + recipeId));

        recipe.setName(updatedRecipe.getName());
        recipe.setDescription(updatedRecipe.getDescription());
        recipe.setSourceType(updatedRecipe.getSourceType());
        recipe.setSourceValue(updatedRecipe.getSourceValue());
        recipe.setPrepTime(updatedRecipe.getPrepTime());
        recipe.setCookTime(updatedRecipe.getCookTime());
        recipe.setServings(updatedRecipe.getServings());
        recipe.setCategory(updatedRecipe.getCategory());
        recipe.setImageUrl(updatedRecipe.getImageUrl());

        // Clear existing ingredients and add new ones
        recipe.getIngredients().clear();
        if (updatedRecipe.getIngredients() != null) {
            updatedRecipe.getIngredients().forEach(ingredientDto -> {
                com.lcupery.recipe_app.entity.Ingredient ingredient =
                    com.lcupery.recipe_app.mapper.IngredientMapper.mapToIngredient(ingredientDto);
                ingredient.setRecipe(recipe);
                recipe.getIngredients().add(ingredient);
            });
        }

        // Clear existing steps and add new ones
        recipe.getSteps().clear();
        if (updatedRecipe.getSteps() != null) {
            updatedRecipe.getSteps().forEach(stepDto -> {
                com.lcupery.recipe_app.entity.Step step =
                    com.lcupery.recipe_app.mapper.StepMapper.mapToStep(stepDto);
                step.setRecipe(recipe);
                recipe.getSteps().add(step);
            });
        }

        Recipe updatedRecipeObj = recipeRepository.save(recipe);
        return RecipeMapper.mapToRecipeDto(updatedRecipeObj);
    }

    @Override
    public void deleteRecipe(Long recipeId, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe does not exist with id: " + recipeId));
        recipeRepository.deleteById(recipeId);
    }
}
