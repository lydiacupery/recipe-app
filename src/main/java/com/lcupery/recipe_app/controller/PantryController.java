package com.lcupery.recipe_app.controller;

import com.lcupery.recipe_app.dto.PantryItemDto;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.service.PantryService;
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
@RequestMapping("/api/pantry")
public class PantryController {

    private PantryService pantryService;
    private UserService userService;

    // GET /api/pantry - Get all pantry items
    @GetMapping
    public ResponseEntity<List<PantryItemDto>> getAllPantryItems(Authentication authentication) {
        User user = getUserFromJwt(authentication);
        List<PantryItemDto> items = pantryService.getAllPantryItems(user);
        return ResponseEntity.ok(items);
    }

    // POST /api/pantry - Add item to pantry
    @PostMapping
    public ResponseEntity<PantryItemDto> addPantryItem(
            @RequestBody PantryItemDto pantryItemDto,
            Authentication authentication) {
        try {
            User user = getUserFromJwt(authentication);
            PantryItemDto created = pantryService.addPantryItem(pantryItemDto, user);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalStateException e) {
            log.warn("Duplicate pantry item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    // PATCH /api/pantry/{id} - Update item (change location)
    @PatchMapping("/{id}")
    public ResponseEntity<PantryItemDto> updatePantryItem(
            @PathVariable Long id,
            @RequestBody PantryItemDto pantryItemDto,
            Authentication authentication) {
        User user = getUserFromJwt(authentication);
        PantryItemDto updated = pantryService.updatePantryItem(id, pantryItemDto, user);
        return ResponseEntity.ok(updated);
    }

    // DELETE /api/pantry/{id} - Remove item from pantry
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePantryItem(
            @PathVariable Long id,
            Authentication authentication) {
        User user = getUserFromJwt(authentication);
        pantryService.deletePantryItem(id, user);
        return ResponseEntity.ok("Pantry item deleted");
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
