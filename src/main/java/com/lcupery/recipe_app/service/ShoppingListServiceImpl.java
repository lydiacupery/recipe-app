package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.ShoppingListItemDto;
import com.lcupery.recipe_app.entity.Recipe;
import com.lcupery.recipe_app.entity.ShoppingListItem;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.mapper.ShoppingListItemMapper;
import com.lcupery.recipe_app.repository.RecipeRepository;
import com.lcupery.recipe_app.repository.ShoppingListItemRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ShoppingListServiceImpl implements ShoppingListService {

    private ShoppingListItemRepository shoppingListItemRepository;
    private RecipeRepository recipeRepository;

    @Override
    @Transactional
    public List<ShoppingListItemDto> addRecipeToShoppingList(Long recipeId, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        // Check if recipe already in shopping list
        List<ShoppingListItem> existing = shoppingListItemRepository.findByUserAndRecipe(user, recipe);
        if (!existing.isEmpty()) {
            throw new IllegalStateException("Recipe already in shopping list");
        }

        // Add all ingredients from recipe
        List<ShoppingListItem> items = recipe.getIngredients().stream()
                .map(ingredient -> {
                    ShoppingListItem item = new ShoppingListItem();
                    item.setUser(user);
                    item.setRecipe(recipe);
                    item.setIngredientName(ingredient.getName());
                    item.setIngredientQuantity(ingredient.getQuantity());
                    item.setChecked(false);
                    return item;
                })
                .collect(Collectors.toList());

        List<ShoppingListItem> saved = shoppingListItemRepository.saveAll(items);
        log.info("Added {} ingredients from recipe {} to shopping list for user {}",
                saved.size(), recipeId, user.getAuth0Id());

        return saved.stream()
                .map(ShoppingListItemMapper::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeRecipeFromShoppingList(Long recipeId, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        shoppingListItemRepository.deleteByUserAndRecipe(user, recipe);
        log.info("Removed all items from recipe {} for user {}", recipeId, user.getAuth0Id());
    }

    @Override
    public List<ShoppingListItemDto> getShoppingList(User user) {
        List<ShoppingListItem> items = shoppingListItemRepository.findByUserOrderByIngredientNameAsc(user);
        return items.stream()
                .map(ShoppingListItemMapper::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ShoppingListItemDto toggleItemChecked(Long itemId, User user) {
        ShoppingListItem item = shoppingListItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shopping list item not found: " + itemId));

        // Verify ownership
        if (!item.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access to shopping list item");
        }

        item.setChecked(!item.isChecked());
        ShoppingListItem saved = shoppingListItemRepository.save(item);
        log.debug("Toggled item {} checked status to {} for user {}",
                itemId, saved.isChecked(), user.getAuth0Id());

        return ShoppingListItemMapper.mapToDto(saved);
    }

    @Override
    @Transactional
    public void clearCheckedItems(User user) {
        List<ShoppingListItem> checkedItems = shoppingListItemRepository
                .findByUserOrderByIngredientNameAsc(user)
                .stream()
                .filter(ShoppingListItem::isChecked)
                .collect(Collectors.toList());

        shoppingListItemRepository.deleteAll(checkedItems);
        log.info("Cleared {} checked items for user {}", checkedItems.size(), user.getAuth0Id());
    }

    @Override
    @Transactional
    public void clearShoppingList(User user) {
        List<ShoppingListItem> allItems = shoppingListItemRepository.findByUserOrderByIngredientNameAsc(user);
        shoppingListItemRepository.deleteAll(allItems);
        log.info("Cleared entire shopping list ({} items) for user {}", allItems.size(), user.getAuth0Id());
    }
}
