package com.lcupery.recipe_app.controller;

import com.lcupery.recipe_app.dto.ShoppingListItemDto;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.service.ShoppingListService;
import com.lcupery.recipe_app.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@CrossOrigin("*")
@AllArgsConstructor
@RestController
@RequestMapping("/api/shopping-list")
public class ShoppingListController {

    private ShoppingListService shoppingListService;
    private UserService userService;

    // GET /api/shopping-list - Get all shopping list items
    @GetMapping
    public ResponseEntity<List<ShoppingListItemDto>> getShoppingList(Authentication authentication) {
        User user = getUserFromJwt(authentication);
        List<ShoppingListItemDto> items = shoppingListService.getShoppingList(user);
        return ResponseEntity.ok(items);
    }

    // POST /api/shopping-list/recipes/{recipeId} - Add recipe to shopping list
    @PostMapping("/recipes/{recipeId}")
    public ResponseEntity<List<ShoppingListItemDto>> addRecipeToShoppingList(
            @PathVariable Long recipeId,
            Authentication authentication) {
        try {
            User user = getUserFromJwt(authentication);
            List<ShoppingListItemDto> items = shoppingListService.addRecipeToShoppingList(recipeId, user);
            return ResponseEntity.ok(items);
        } catch (IllegalStateException e) {
            log.warn("Recipe {} already in shopping list", recipeId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    // DELETE /api/shopping-list/recipes/{recipeId} - Remove recipe from shopping list
    @DeleteMapping("/recipes/{recipeId}")
    public ResponseEntity<String> removeRecipeFromShoppingList(
            @PathVariable Long recipeId,
            Authentication authentication) {
        User user = getUserFromJwt(authentication);
        shoppingListService.removeRecipeFromShoppingList(recipeId, user);
        return ResponseEntity.ok("Recipe removed from shopping list");
    }

    // PATCH /api/shopping-list/items/{itemId}/toggle - Toggle checked status
    @PatchMapping("/items/{itemId}/toggle")
    public ResponseEntity<ShoppingListItemDto> toggleItemChecked(
            @PathVariable Long itemId,
            Authentication authentication) {
        User user = getUserFromJwt(authentication);
        ShoppingListItemDto item = shoppingListService.toggleItemChecked(itemId, user);
        return ResponseEntity.ok(item);
    }

    // DELETE /api/shopping-list/checked - Clear checked items
    @DeleteMapping("/checked")
    public ResponseEntity<String> clearCheckedItems(Authentication authentication) {
        User user = getUserFromJwt(authentication);
        shoppingListService.clearCheckedItems(user);
        return ResponseEntity.ok("Checked items cleared");
    }

    // DELETE /api/shopping-list - Clear entire shopping list
    @DeleteMapping
    public ResponseEntity<String> clearShoppingList(Authentication authentication) {
        User user = getUserFromJwt(authentication);
        shoppingListService.clearShoppingList(user);
        return ResponseEntity.ok("Shopping list cleared");
    }

    // Helper method to get or create user from JWT token
    private User getUserFromJwt(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String auth0Id = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        return userService.findOrCreateUser(auth0Id, email, name);
    }
}
