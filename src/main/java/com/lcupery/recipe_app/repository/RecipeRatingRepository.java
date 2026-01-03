package com.lcupery.recipe_app.repository;

import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.RecipeRating;
import com.lcupery.recipe_app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeRatingRepository extends JpaRepository<RecipeRating, Long> {
    List<RecipeRating> findByRecipe(Recipe recipe);
    List<RecipeRating> findByRecipeOrderByCreatedAtDesc(Recipe recipe);

    @Query("SELECT r FROM RecipeRating r JOIN FETCH r.recipe rec JOIN FETCH rec.user WHERE r.id = :id")
    Optional<RecipeRating> findByIdWithRecipeAndUser(@Param("id") Long id);
}
