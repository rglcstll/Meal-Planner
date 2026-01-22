package com.example.demo.controller;

import com.example.demo.dto.ShoppingListDTO;
import com.example.demo.service.ShoppingListService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    /**
     * Generate shopping list from a meal plan by its ID.
     * GET /api/shopping-list/{mealPlanId}
     */
    @GetMapping("/{mealPlanId}")
    public ResponseEntity<ShoppingListDTO> getShoppingListByMealPlanId(@PathVariable Long mealPlanId) {
        log.info("Received request to generate shopping list for meal plan ID: {}", mealPlanId);

        try {
            ShoppingListDTO shoppingList = shoppingListService.generateShoppingListByMealPlanId(mealPlanId);

            if (shoppingList == null) {
                log.warn("Shopping list generation returned null for meal plan ID: {}", mealPlanId);
                return ResponseEntity.notFound().build();
            }

            log.info("Successfully generated shopping list with {} items for meal plan ID: {}",
                     shoppingList.getTotalItems(), mealPlanId);
            return ResponseEntity.ok(shoppingList);

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
    public ResponseEntity<ShoppingListDTO> getShoppingListByDate(@RequestParam String date) {
        log.info("Received request to generate shopping list for date: {}", date);

        try {
            LocalDate planDate = LocalDate.parse(date);
            ShoppingListDTO shoppingList = shoppingListService.generateShoppingListByDate(planDate);

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
            @RequestBody Map<String, Map<String, String>> mealsMap) {
        log.info("Received request to generate shopping list from meals map");

        try {
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
