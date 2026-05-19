package com.example.demo.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.SecurityConfig;
import com.example.demo.model.Recipe;
import com.example.demo.model.User;
import com.example.demo.repository.FoodRepository;
import com.example.demo.service.AllergyService;
import com.example.demo.service.MealGenerationService;
import com.example.demo.service.RecipeService;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FoodController.class)
@Import({SecurityConfig.class, FoodControllerWebTest.StubConfig.class})
@ActiveProfiles("test")
class FoodControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubConfig.MutableUserService userService;

    @Autowired
    private StubConfig.MutableRecipeService recipeService;

    @Autowired
    private StubConfig.MutableAllergyService allergyService;

    @Autowired
    private StubConfig.MutableMealGenerationService mealGenerationService;

    @BeforeEach
    void resetStubs() {
        userService.currentUser = buildUser(1L, "alice@example.com");
        recipeService.recipes = Collections.emptyList();
        recipeService.exception = null;
        allergyService.filteredRecipes = null;
        mealGenerationService.plan = fullPlan("Fallback Meal");
        mealGenerationService.exception = null;
    }

    @Test
    void rejectsForeignUserIdWithForbidden() throws Exception {
        mockMvc.perform(get("/api/foods/generate-meal-plan/standard")
                        .with(user("alice@example.com"))
                        .param("userId", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidUserIdWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/foods/generate-meal-plan/standard")
                        .with(user("alice@example.com"))
                        .param("userId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsGeneratedPlanForValidRequest() throws Exception {
        Recipe recipe = new Recipe();
        recipe.setId(10L);
        recipe.setName("Oatmeal");
        recipe.setDietaryTags(List.of("breakfast", "vegetarian"));
        recipe.setAllergens(Set.of());
        recipe.setTotalCalories(420);

        recipeService.recipes = List.of(recipe);
        allergyService.filteredRecipes = List.of(recipe);
        mealGenerationService.plan = fullPlan("Oatmeal");

        mockMvc.perform(get("/api/foods/generate-meal-plan/vegetarian")
                        .with(user("alice@example.com"))
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.MONDAY.BREAKFAST").value("Oatmeal"))
                .andExpect(jsonPath("$.SUNDAY.DINNER").value("Oatmeal"));
    }

    @Test
    void returnsInternalServerErrorWhenGenerationCrashes() throws Exception {
        recipeService.exception = new RuntimeException("boom");

        mockMvc.perform(get("/api/foods/generate-meal-plan/standard")
                        .with(user("alice@example.com")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.MONDAY.BREAKFAST").value("Server error generating plan."));
    }

    private User buildUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setAge(30);
        user.setHeight(175);
        user.setWeight(75);
        user.setGender("male");
        user.setActivityLevel("moderate");
        user.setVerified(true);
        return user;
    }

    private Map<String, Map<String, String>> fullPlan(String mealName) {
        Map<String, Map<String, String>> plan = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        for (String day : days) {
            Map<String, String> dayMeals = new LinkedHashMap<>();
            dayMeals.put("BREAKFAST", mealName);
            dayMeals.put("LUNCH", mealName);
            dayMeals.put("DINNER", mealName);
            plan.put(day, dayMeals);
        }
        return plan;
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        FoodRepository foodRepository() {
            return (FoodRepository) Proxy.newProxyInstance(
                    FoodRepository.class.getClassLoader(),
                    new Class<?>[]{FoodRepository.class},
                    (proxy, method, args) -> {
                        Class<?> returnType = method.getReturnType();
                        if (List.class.isAssignableFrom(returnType)) {
                            return Collections.emptyList();
                        }
                        if (boolean.class.equals(returnType)) {
                            return false;
                        }
                        if (long.class.equals(returnType) || int.class.equals(returnType)) {
                            return 0;
                        }
                        return null;
                    }
            );
        }

        @Bean
        MutableUserService userService() {
            return new MutableUserService();
        }

        @Bean
        MutableRecipeService recipeService() {
            return new MutableRecipeService();
        }

        @Bean
        MutableAllergyService allergyService() {
            return new MutableAllergyService();
        }

        @Bean
        MutableMealGenerationService mealGenerationService() {
            return new MutableMealGenerationService();
        }

        static class MutableUserService extends UserService {
            private User currentUser;

            MutableUserService() {
                super(null, null, null);
            }

            @Override
            public User getUserByEmail(String email) {
                return currentUser;
            }
        }

        static class MutableRecipeService extends RecipeService {
            private List<Recipe> recipes = Collections.emptyList();
            private RuntimeException exception;

            MutableRecipeService() {
                super(null, null);
            }

            @Override
            public List<Recipe> getAllAvailableRecipes() {
                if (exception != null) {
                    throw exception;
                }
                return recipes;
            }
        }

        static class MutableAllergyService extends AllergyService {
            private List<Recipe> filteredRecipes;

            MutableAllergyService() {
                super(null, null, null);
            }

            @Override
            public List<Recipe> filterRecipesByAllergies(List<Recipe> recipesToFilter, String userIdString) {
                return filteredRecipes != null ? filteredRecipes : recipesToFilter;
            }
        }

        static class MutableMealGenerationService extends MealGenerationService {
            private Map<String, Map<String, String>> plan = Collections.emptyMap();
            private RuntimeException exception;

            @Override
            public Map<String, Map<String, String>> generateWeeklyPlanDeterministic(
                    List<Recipe> safeRecipes,
                    String dietType,
                    User currentUser) {
                if (exception != null) {
                    throw exception;
                }
                return plan;
            }
        }
    }
}
