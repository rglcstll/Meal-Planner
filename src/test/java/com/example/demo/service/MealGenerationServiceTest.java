package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.Recipe;
import com.example.demo.model.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MealGenerationServiceTest {

    private final MealGenerationService mealGenerationService = new MealGenerationService();

    @Test
    void deterministicGenerationReturnsSameOutputForSameInput() {
        List<Recipe> recipes = List.of(
                recipe(1L, "Oatmeal Bowl", 420, List.of("breakfast", "vegetarian")),
                recipe(2L, "Garden Salad", 620, List.of("lunch", "vegetarian")),
                recipe(3L, "Vegetable Stir Fry", 780, List.of("dinner", "vegetarian"))
        );

        Map<String, Map<String, String>> first = mealGenerationService.generateWeeklyPlanDeterministic(recipes, "vegetarian", completeUser());
        Map<String, Map<String, String>> second = mealGenerationService.generateWeeklyPlanDeterministic(recipes, "vegetarian", completeUser());

        assertEquals(first, second);
    }

    @Test
    void tieBreakIsStableByLowerRecipeId() {
        List<Recipe> recipes = List.of(
                recipe(2L, "Breakfast B", 500, List.of("breakfast")),
                recipe(1L, "Breakfast A", 500, List.of("breakfast")),
                recipe(3L, "Lunch Option", 700, List.of("lunch")),
                recipe(4L, "Dinner Option", 800, List.of("dinner"))
        );

        Map<String, Map<String, String>> plan = mealGenerationService.generateWeeklyPlanDeterministic(recipes, "standard", completeUser());
        assertEquals("Breakfast A", plan.get("MONDAY").get("BREAKFAST"));
    }

    @Test
    void nullAndPartialFieldsDoNotThrow() {
        Recipe nullHeavy = new Recipe();
        nullHeavy.setId(11L);
        nullHeavy.setName(null);
        nullHeavy.setDietaryTags(null);
        nullHeavy.setAllergens(null);
        nullHeavy.setTotalCalories(null);

        Recipe valid = recipe(12L, "Fallback Meal", 650, List.of("lunch"));

        assertDoesNotThrow(() ->
                mealGenerationService.generateWeeklyPlanDeterministic(List.of(nullHeavy, valid), "standard", incompleteUser())
        );
    }

    @Test
    void diversityPenaltyReducesWeeklyRepeats() {
        List<Recipe> recipes = List.of(
                recipe(10L, "Protein Oatmeal", 420, List.of("breakfast")),
                recipe(11L, "Greek Yogurt Bowl", 430, List.of("breakfast")),
                recipe(12L, "Lunch Plate", 700, List.of("lunch")),
                recipe(13L, "Dinner Plate", 800, List.of("dinner"))
        );

        Map<String, Map<String, String>> plan = mealGenerationService.generateWeeklyPlanDeterministic(recipes, "standard", completeUser());
        Set<String> breakfastMeals = plan.values().stream()
                .map(dayMeals -> dayMeals.get("BREAKFAST"))
                .collect(Collectors.toSet());

        assertTrue(breakfastMeals.size() > 1, "Expected more than one unique breakfast across the week.");
    }

    private Recipe recipe(Long id, String name, Integer calories, List<String> tags) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setTotalCalories(calories);
        recipe.setDietaryTags(tags);
        recipe.setAllergens(Set.of());
        return recipe;
    }

    private User completeUser() {
        User user = new User();
        user.setAge(30);
        user.setHeight(175);
        user.setWeight(75);
        user.setGender("male");
        user.setActivityLevel("moderate");
        return user;
    }

    private User incompleteUser() {
        User user = new User();
        user.setGender("female");
        return user;
    }
}
