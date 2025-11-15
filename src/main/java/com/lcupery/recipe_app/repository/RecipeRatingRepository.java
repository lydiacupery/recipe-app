package com.lcupery.recipe_app.repository;

import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.RecipeRating;
import com.lcupery.recipe_app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeRatingRepository extends JpaRepository<RecipeRating, Long> {
    List<RecipeRating> findByRecipe(Recipe recipe);
    Optional<RecipeRating> findByRecipeAndUser(Recipe recipe, User user);
    List<RecipeRating> findByRecipeOrderByCreatedAtDesc(Recipe recipe);
}
