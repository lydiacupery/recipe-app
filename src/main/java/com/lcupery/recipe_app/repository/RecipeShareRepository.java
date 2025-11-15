package com.lcupery.recipe_app.repository;

import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.RecipeShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecipeShareRepository extends JpaRepository<RecipeShare, String> {
    Optional<RecipeShare> findByToken(String token);
    Optional<RecipeShare> findByRecipe(Recipe recipe);
}
