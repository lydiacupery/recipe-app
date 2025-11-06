package com.lcupery.recipe_app.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = RecipeSourceValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRecipeSource {
    String message() default "Source value must be a valid URL when source type is URL";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
