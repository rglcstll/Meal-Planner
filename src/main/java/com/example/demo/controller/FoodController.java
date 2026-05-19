package com.example.demo.controller;

import com.example.demo.model.Food;
import com.example.demo.model.Recipe; // Ensure this is your Recipe model
import com.example.demo.model.User;
import com.example.demo.repository.FoodRepository;
import com.example.demo.service.AllergyService;
import com.example.demo.service.MealGenerationService; // For new meal plan generation
import com.example.demo.service.RecipeService;     // For fetching recipes
import com.example.demo.service.UserService;
// import com.example.demo.service.MealPlanService; // This was used by the old generateMealPlan logic.
                                                 // Remove if no other methods in this controller use it.

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList; // For getAllFoods fallback
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/foods") // Matches your dashboard.js foodApiUrl
public class FoodController {

    private static final Logger logger = LoggerFactory.getLogger(FoodController.class);

    // Dependencies for the controller
    private final FoodRepository foodRepository; // For getAllFoods()
    private final AllergyService allergyService; // For allergy filtering
    private final RecipeService recipeService; // For fetching all recipes
    private final MealGenerationService mealGenerationService; // For generating the plan structure
    private final UserService userService;

    // If MealPlanService is used by other methods not shown, add it back to constructor and field.
    // private final MealPlanService mealPlanService;

    @Autowired
    public FoodController(FoodRepository foodRepository,
                          AllergyService allergyService,
                          RecipeService recipeService,
                          MealGenerationService mealGenerationService,
                          UserService userService
                          /*, MealPlanService mealPlanService */) {
        this.foodRepository = foodRepository;
        this.allergyService = allergyService;
        this.recipeService = recipeService;
        this.mealGenerationService = mealGenerationService;
        this.userService = userService;
        // this.mealPlanService = mealPlanService; // Initialize if kept
    }

    /**
     * Generates a weekly meal plan based on the specified diet type and, optionally, user allergies.
     * This method now uses RecipeService for fetching recipes, AllergyService for filtering,
     * and MealGenerationService for constructing the plan.
     *
     * @param dietType The desired dietary preference (e.g., "standard", "vegetarian").
     * @param userId   Optional user ID (as a String) to filter meals based on user allergies.
     * @return A ResponseEntity containing the generated meal plan map (Day -> MealType -> MealName)
     * or an appropriate error status.
     */
    @GetMapping("/generate-meal-plan/{dietType}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Map<String, String>>> generateMealPlan(
            @PathVariable String dietType,
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal UserDetails principal) {

        logger.info("Controller: Request to generate meal plan. Diet: '{}', UserID: '{}'",
                dietType, userId != null ? userId : "N/A");

        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createEmptyPlan("User not authenticated."));
            }

            User authenticatedUser = userService.getUserByEmail(principal.getUsername());
            if (authenticatedUser == null || authenticatedUser.getId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createEmptyPlan("Authenticated user not found."));
            }
            Long authenticatedUserId = authenticatedUser.getId();

            Long requestedUserId = null;
            if (hasText(userId) && !"default".equalsIgnoreCase(userId)) {
                try {
                    requestedUserId = Long.parseLong(userId);
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body(createEmptyPlan("Invalid userId parameter."));
                }
            }
            if (requestedUserId != null && !requestedUserId.equals(authenticatedUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createEmptyPlan("Forbidden userId access."));
            }
            String effectiveUserId = requestedUserId != null ? requestedUserId.toString() : null;

            List<Recipe> allAvailableRecipes = recipeService.getAllAvailableRecipes();
            if (allAvailableRecipes == null) {
                allAvailableRecipes = Collections.emptyList();
            }
            int beforeDietCount = allAvailableRecipes.size();

            List<Recipe> dietFilteredRecipes = filterRecipesByDiet(allAvailableRecipes, dietType);
            int afterDietCount = dietFilteredRecipes.size();

            List<Recipe> allergyFilteredRecipes = dietFilteredRecipes;
            if (effectiveUserId != null) {
                allergyFilteredRecipes = allergyService.filterRecipesByAllergies(dietFilteredRecipes, effectiveUserId);
            }
            int afterAllergyCount = allergyFilteredRecipes != null ? allergyFilteredRecipes.size() : 0;

            logger.info("Meal-generation pipeline counts: dietType={}, userId={}, beforeDiet={}, afterDiet={}, afterAllergy={}",
                    safeLower(dietType), effectiveUserId != null ? effectiveUserId : authenticatedUserId, beforeDietCount, afterDietCount, afterAllergyCount);

            Map<String, Map<String, String>> weeklyPlan = mealGenerationService.generateWeeklyPlanDeterministic(
                    allergyFilteredRecipes, dietType, authenticatedUser
            );

            if (weeklyPlan == null || weeklyPlan.isEmpty()) {
                return ResponseEntity.ok(createEmptyPlan("No suitable meals available."));
            }
            return ResponseEntity.ok(weeklyPlan);

        } catch (IllegalArgumentException e) {
            logger.warn("Meal generation request rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createEmptyPlan("Invalid request."));
        } catch (Exception e) {
            logger.error("Critical error in FoodController while generating meal plan for diet '{}', UserID '{}': {}",
                    dietType, userId != null ? userId : "N/A", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(createEmptyPlan("Server error generating plan."));
        }
    }

    private List<Recipe> filterRecipesByDiet(List<Recipe> recipes, String dietType) {
        String normalizedDiet = safeLower(dietType);
        if (!hasText(normalizedDiet) || "standard".equals(normalizedDiet) || "balanced".equals(normalizedDiet)) {
            return new ArrayList<>(recipes);
        }
        return recipes.stream()
                .filter(Objects::nonNull)
                .filter(recipe -> recipe.getDietaryTags() != null && recipe.getDietaryTags().stream()
                        .filter(Objects::nonNull)
                        .map(this::safeLower)
                        .anyMatch(normalizedDiet::equals))
                .collect(Collectors.toList());
    }

    /**
     * Helper method to create an empty meal plan map, potentially with a default message.
     * Used as a fallback response when generation fails or yields no results.
     * (This method is from your original FoodController.java)
     * @param defaultMessage The message to put in meal slots (e.g., "N/A" or a specific message).
     * @return A Map representing an empty weekly meal plan structure.
     */
    private Map<String, Map<String, String>> createEmptyPlan(String defaultMessage) {
        Map<String, Map<String, String>> emptyPlan = new HashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            dayMeals.put("BREAKFAST", defaultMessage);
            dayMeals.put("LUNCH", defaultMessage);
            dayMeals.put("DINNER", defaultMessage);
            emptyPlan.put(day, dayMeals);
        }
        logger.debug("Created empty plan structure with default message: {}", defaultMessage);
        return emptyPlan;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Gets all foods.
     * (This method is from your original FoodController.java)
     */
    @GetMapping
    public List<Food> getAllFoods() {
        logger.info("Controller received request to get all foods.");
        return foodRepository.findAll();
    }
}
