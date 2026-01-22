package com.example.demo.service;

import com.example.demo.model.Recipe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for generating weekly meal plans from available recipes.
 * Distributes recipes across days and meal types (breakfast, lunch, dinner).
 */
@Service
public class MealGenerationService {

    private static final Logger log = LoggerFactory.getLogger(MealGenerationService.class);

    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final String[] MEAL_TYPES = {"BREAKFAST", "LUNCH", "DINNER"};
    private static final String NO_RECIPE_MESSAGE = "No suitable recipe found";

    /**
     * Generates a weekly meal plan from the provided list of recipes.
     * Recipes are categorized by meal type based on their dietary tags,
     * then distributed across the week.
     *
     * @param recipes List of recipes to use for plan generation
     * @return Map structure: Day -> MealType -> RecipeName
     */
    public Map<String, Map<String, String>> generateWeeklyPlan(List<Recipe> recipes) {
        log.info("Generating weekly meal plan from {} recipes", recipes != null ? recipes.size() : 0);

        Map<String, Map<String, String>> weeklyPlan = new LinkedHashMap<>();

        // Initialize the plan structure
        for (String day : DAYS) {
            weeklyPlan.put(day, new LinkedHashMap<>());
            for (String mealType : MEAL_TYPES) {
                weeklyPlan.get(day).put(mealType, NO_RECIPE_MESSAGE);
            }
        }

        if (recipes == null || recipes.isEmpty()) {
            log.warn("No recipes provided for meal plan generation. Returning empty plan.");
            return weeklyPlan;
        }

        // Categorize recipes by meal type
        List<Recipe> breakfastRecipes = new ArrayList<>();
        List<Recipe> lunchRecipes = new ArrayList<>();
        List<Recipe> dinnerRecipes = new ArrayList<>();
        List<Recipe> uncategorizedRecipes = new ArrayList<>();

        for (Recipe recipe : recipes) {
            boolean categorized = false;
            List<String> tags = recipe.getDietaryTags();

            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    String lowerTag = tag.toLowerCase();
                    if (lowerTag.contains("breakfast")) {
                        breakfastRecipes.add(recipe);
                        categorized = true;
                        break;
                    } else if (lowerTag.contains("lunch")) {
                        lunchRecipes.add(recipe);
                        categorized = true;
                        break;
                    } else if (lowerTag.contains("dinner") || lowerTag.contains("supper")) {
                        dinnerRecipes.add(recipe);
                        categorized = true;
                        break;
                    }
                }
            }

            if (!categorized) {
                uncategorizedRecipes.add(recipe);
            }
        }

        log.debug("Categorized recipes - Breakfast: {}, Lunch: {}, Dinner: {}, Uncategorized: {}",
                breakfastRecipes.size(), lunchRecipes.size(), dinnerRecipes.size(), uncategorizedRecipes.size());

        // Distribute uncategorized recipes to fill gaps
        distributeUncategorizedRecipes(breakfastRecipes, lunchRecipes, dinnerRecipes, uncategorizedRecipes);

        // Shuffle for variety
        Collections.shuffle(breakfastRecipes);
        Collections.shuffle(lunchRecipes);
        Collections.shuffle(dinnerRecipes);

        // Assign recipes to each day
        for (int i = 0; i < DAYS.length; i++) {
            String day = DAYS[i];
            Map<String, String> dayMeals = weeklyPlan.get(day);

            // Assign breakfast
            if (!breakfastRecipes.isEmpty()) {
                Recipe recipe = breakfastRecipes.get(i % breakfastRecipes.size());
                dayMeals.put("BREAKFAST", recipe.getName());
            }

            // Assign lunch
            if (!lunchRecipes.isEmpty()) {
                Recipe recipe = lunchRecipes.get(i % lunchRecipes.size());
                dayMeals.put("LUNCH", recipe.getName());
            }

            // Assign dinner
            if (!dinnerRecipes.isEmpty()) {
                Recipe recipe = dinnerRecipes.get(i % dinnerRecipes.size());
                dayMeals.put("DINNER", recipe.getName());
            }
        }

        log.info("Successfully generated weekly meal plan");
        return weeklyPlan;
    }

    /**
     * Distributes uncategorized recipes to meal categories that need more variety.
     * Uses calorie-based heuristics: lower calorie meals tend to be breakfast,
     * higher calorie meals tend to be dinner.
     */
    private void distributeUncategorizedRecipes(List<Recipe> breakfast, List<Recipe> lunch,
                                                  List<Recipe> dinner, List<Recipe> uncategorized) {
        if (uncategorized.isEmpty()) {
            return;
        }

        // Sort uncategorized by calories (lowest first)
        uncategorized.sort(Comparator.comparingInt(r -> r.getTotalCalories() != null ? r.getTotalCalories() : 400));

        int totalUncategorized = uncategorized.size();
        int breakfastCount = totalUncategorized / 3;
        int lunchCount = totalUncategorized / 3;
        // Rest goes to dinner

        int index = 0;

        // Lower calorie items to breakfast
        for (int i = 0; i < breakfastCount && index < totalUncategorized; i++) {
            breakfast.add(uncategorized.get(index++));
        }

        // Medium calorie items to lunch
        for (int i = 0; i < lunchCount && index < totalUncategorized; i++) {
            lunch.add(uncategorized.get(index++));
        }

        // Higher calorie items to dinner
        while (index < totalUncategorized) {
            dinner.add(uncategorized.get(index++));
        }

        log.debug("Distributed {} uncategorized recipes across meal types", totalUncategorized);
    }
}
