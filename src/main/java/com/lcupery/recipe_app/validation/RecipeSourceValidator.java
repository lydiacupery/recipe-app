package com.lcupery.recipe_app.validation;

import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.SourceType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.MalformedURLException;
import java.net.URL;

public class RecipeSourceValidator implements ConstraintValidator<ValidRecipeSource, RecipeDto> {

    @Override
    public void initialize(ValidRecipeSource constraintAnnotation) {
    }

    @Override
    public boolean isValid(RecipeDto recipeDto, ConstraintValidatorContext context) {
        if (recipeDto == null) {
            return true;
        }

        // If sourceType is null or sourceValue is null, allow other validations to handle it
        if (recipeDto.getSourceType() == null || recipeDto.getSourceValue() == null) {
            return true;
        }

        // If sourceType is URL, validate that sourceValue is a valid URL
        if (recipeDto.getSourceType() == SourceType.URL) {
            try {
                new URL(recipeDto.getSourceValue());
                return true;
            } catch (MalformedURLException e) {
                return false;
            }
        }

        // For TEXT type, any non-null value is valid
        return true;
    }
}
