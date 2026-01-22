package com.example.demo.controller;

import com.example.demo.model.Food;
import com.example.demo.model.Recipe; // Ensure this is your Recipe model
import com.example.demo.repository.FoodRepository;
import com.example.demo.service.AllergyService;
import com.example.demo.service.MealGenerationService; // For new meal plan generation
import com.example.demo.service.RecipeService;     // For fetching recipes
// import com.example.demo.service.MealPlanService; // This was used by the old generateMealPlan logic.
                                                 // Remove if no other methods in this controller use it.

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList; // For getAllFoods fallback
import java.util.stream.Collectors; // Required for the diet filtering example if you use it

@RestController
@RequestMapping("/api/foods") // Matches your dashboard.js foodApiUrl
public class FoodController {

    private static final Logger logger = LoggerFactory.getLogger(FoodController.class);

    // Dependencies for the controller
    private final FoodRepository foodRepository; // For getAllFoods()
    private final AllergyService allergyService; // For allergy filtering
    private final RecipeService recipeService; // For fetching all recipes
    private final MealGenerationService mealGenerationService; // For generating the plan structure

    // If MealPlanService is used by other methods not shown, add it back to constructor and field.
    // private final MealPlanService mealPlanService;

    @Autowired
    public FoodController(FoodRepository foodRepository,
                          AllergyService allergyService,
                          RecipeService recipeService,
                          MealGenerationService mealGenerationService
                          /*, MealPlanService mealPlanService */) {
        this.foodRepository = foodRepository;
        this.allergyService = allergyService;
        this.recipeService = recipeService;
        this.mealGenerationService = mealGenerationService;
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
    public ResponseEntity<Map<String, Map<String, String>>> generateMealPlan(
            @PathVariable String dietType,
            @RequestParam(required = false) String userId) {

        logger.info("Controller: Request to generate meal plan. Diet: '{}', UserID: '{}'",
                dietType, userId != null ? userId : "N/A");

        try {
            // 1. Fetch all available recipes from the database
            List<Recipe> allAvailableRecipes = recipeService.getAllAvailableRecipes(); // From RecipeService_updated_with_getAllAvailable
            if (allAvailableRecipes == null) {
                allAvailableRecipes = Collections.emptyList();
            }

            if (allAvailableRecipes.isEmpty()) {
                logger.warn("No recipes available in the database. Meal plan will use placeholders from MealGenerationService.");
            }
            logger.debug("Fetched {} recipes initially.", allAvailableRecipes.size());

            List<Recipe> recipesToConsider = new ArrayList<>(allAvailableRecipes); // Use a mutable list

            // 2. Filter recipes by allergies if a valid userId is provided
            if (userId != null && !userId.isBlank() && !"default".equalsIgnoreCase(userId)) {
                try {
                    Long numericUserId = Long.parseLong(userId);
                    logger.info("Filtering recipes for user ID: {} (Diet: {})", numericUserId, dietType);

                    // Use AllergyService to filter the fetched recipes
                    List<Recipe> filteredByAllergyRecipes = allergyService.filterRecipesByAllergies(recipesToConsider, numericUserId);

                    if (filteredByAllergyRecipes.isEmpty() && !recipesToConsider.isEmpty()) {
                        logger.warn("Allergy filtering for user ID {} resulted in zero recipes. Diet: {}. " +
                                  "The plan will likely contain 'No suitable recipe found' for many slots.", numericUserId, dietType);
                    }
                    recipesToConsider = filteredByAllergyRecipes; // Update the list to consider
                    logger.info("After allergy filtering for user ID {}, {} recipes remaining to consider.", numericUserId, recipesToConsider.size());

                } catch (NumberFormatException e) {
                    logger.warn("Invalid userId format: '{}'. Cannot parse to Long for allergy filtering. " +
                              "Proceeding without allergy-specific filtering.", userId, e);
                }
            } else {
                logger.info("No valid userId provided or userId is 'default'. Proceeding without allergy-specific filtering. Diet: {}", dietType);
            }

            // 3. (Optional but Recommended) Filter recipesToConsider further by 'dietType'
            // This requires your Recipe model to have diet tags (e.g., a Set<String> or List<String> getDietaryTags())
            if (!"standard".equalsIgnoreCase(dietType) && !recipesToConsider.isEmpty()) {
                final String lowerDietType = dietType.toLowerCase();
                // *** FIXED METHOD NAME HERE ***
                recipesToConsider.removeIf(recipe -> recipe.getDietaryTags() == null ||
                                                     recipe.getDietaryTags().stream()
                                                           .noneMatch(tag -> tag.equalsIgnoreCase(lowerDietType)));
                logger.info("After diet filtering for '{}', {} recipes remaining.", dietType, recipesToConsider.size());
                if (recipesToConsider.isEmpty()) {
                    logger.warn("Diet filtering for '{}' resulted in zero recipes. The plan will use placeholders.", dietType);
                }
            }


            // 4. Generate the weekly meal plan using the MealGenerationService
            // MealGenerationService is from the 'meal_generation_service' artifact
            Map<String, Map<String, String>> weeklyPlan = mealGenerationService.generateWeeklyPlan(recipesToConsider);

            if (weeklyPlan == null) { // Should ideally not be null if MealGenerationService is robust
                logger.error("MealGenerationService returned a null plan unexpectedly for diet: {}, userId: {}.",
                        dietType, userId != null ? userId : "N/A");
                return ResponseEntity.ok(createEmptyPlan("Error generating plan. Please try again."));
            }

            logger.info("Controller: Successfully generated and returning meal plan for diet: '{}', UserID: '{}'",
                    dietType, userId != null ? userId : "N/A");
            return ResponseEntity.ok(weeklyPlan);

        } catch (Exception e) {
            logger.error("Critical error in FoodController while generating meal plan for diet '{}', UserID '{}': {}",
                    dietType, userId != null ? userId : "N/A", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(createEmptyPlan("Server error generating plan."));
        }
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
