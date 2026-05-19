package com.example.demo.controller;

import com.example.demo.dto.ShoppingListDTO;
import com.example.demo.service.ShoppingListService;
import com.example.demo.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * REST Controller for Shopping List operations.
 * Provides endpoints to generate shopping lists from meal plans.
 */
@RestController
@RequestMapping("/api/shopping-list")
public class ShoppingListController {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListController.class);

    private final ShoppingListService shoppingListService;
    private final UserService userService;

    @Autowired
    public ShoppingListController(ShoppingListService shoppingListService, UserService userService) {
        this.shoppingListService = shoppingListService;
        this.userService = userService;
    }

    /**
     * Generate shopping list from a meal plan by its ID.
     * GET /api/shopping-list/{mealPlanId}
     */
    @GetMapping("/{mealPlanId}")
    public ResponseEntity<ShoppingListDTO> getShoppingListByMealPlanId(@PathVariable Long mealPlanId,
                                                                        @AuthenticationPrincipal UserDetails principal) {
        log.info("Received request to generate shopping list for meal plan ID: {}", mealPlanId);

        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
            ShoppingListDTO shoppingList = shoppingListService.generateShoppingListByMealPlanId(mealPlanId, authenticatedUserId);

            if (shoppingList == null) {
                log.warn("Shopping list generation returned null for meal plan ID: {}", mealPlanId);
                return ResponseEntity.notFound().build();
            }

            log.info("Successfully generated shopping list with {} items for meal plan ID: {}",
                     shoppingList.getTotalItems(), mealPlanId);
            return ResponseEntity.ok(shoppingList);

        } catch (AccessDeniedException e) {
            log.warn("Forbidden shopping-list access: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error generating shopping list for meal plan ID {}: {}", mealPlanId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate shopping list from a meal plan by its date.
     * GET /api/shopping-list/by-date?date=2024-01-15
     */
    @GetMapping("/by-date")
    public ResponseEntity<ShoppingListDTO> getShoppingListByDate(@RequestParam String date,
                                                                  @AuthenticationPrincipal UserDetails principal) {
        log.info("Received request to generate shopping list for date: {}", date);

        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
            LocalDate planDate = LocalDate.parse(date);
            ShoppingListDTO shoppingList = shoppingListService.generateShoppingListByDate(planDate, authenticatedUserId);

            if (shoppingList == null) {
                log.warn("Shopping list generation returned null for date: {}", date);
                return ResponseEntity.notFound().build();
            }

            log.info("Successfully generated shopping list with {} items for date: {}",
                     shoppingList.getTotalItems(), date);
            return ResponseEntity.ok(shoppingList);

        } catch (DateTimeParseException e) {
            log.error("Invalid date format: {}. Expected yyyy-MM-dd.", date);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error generating shopping list for date {}: {}", date, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate shopping list from a meals map (for unsaved/current plans).
     * POST /api/shopping-list/from-meals
     * Request body: { "MONDAY": { "BREAKFAST": "Recipe Name", ... }, ... }
     */
    @PostMapping("/from-meals")
    public ResponseEntity<ShoppingListDTO> getShoppingListFromMeals(
            @RequestBody Map<String, Map<String, String>> mealsMap,
            @AuthenticationPrincipal UserDetails principal) {
        log.info("Received request to generate shopping list from meals map");

        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (mealsMap == null || mealsMap.isEmpty()) {
                log.warn("Received empty or null meals map");
                return ResponseEntity.badRequest().build();
            }

            ShoppingListDTO shoppingList = shoppingListService.generateShoppingListFromMealsMap(mealsMap);

            if (shoppingList == null) {
                log.warn("Shopping list generation returned null from meals map");
                return ResponseEntity.internalServerError().build();
            }

            log.info("Successfully generated shopping list with {} items from meals map",
                     shoppingList.getTotalItems());
            return ResponseEntity.ok(shoppingList);

        } catch (Exception e) {
            log.error("Error generating shopping list from meals map: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
