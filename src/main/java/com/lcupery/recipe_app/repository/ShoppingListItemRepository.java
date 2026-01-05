package com.lcupery.recipe_app.repository;

import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.ShoppingListItem;
import com.lcupery.recipe_app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {
    List<ShoppingListItem> findByUserOrderByIngredientNameAsc(User user);
    List<ShoppingListItem> findByUserAndRecipe(User user, Recipe recipe);
    void deleteByUserAndRecipe(User user, Recipe recipe);
    int countByUserAndIsChecked(User user, boolean isChecked);
}
