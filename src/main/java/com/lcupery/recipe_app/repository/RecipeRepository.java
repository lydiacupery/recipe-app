package com.lcupery.recipe_app.repository;

import com.lcupery.recipe_app.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByUserId(String userId);
    Optional<Recipe> findByIdAndUserId(Long id, String userId);
}
