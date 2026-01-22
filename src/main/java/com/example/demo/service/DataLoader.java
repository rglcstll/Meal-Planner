package com.example.demo.service;

import com.example.demo.model.Allergy; // Import the Allergy model
import com.example.demo.model.Food;
import com.example.demo.model.Recipe;
import com.example.demo.repository.AllergyRepository; // Import AllergyRepository
import com.example.demo.repository.FoodRepository;
import com.example.demo.repository.RecipeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays; // Required for Arrays.asList
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataLoader {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final FoodRepository foodRepository;
    private final RecipeRepository recipeRepository;
    private final AllergyRepository allergyRepository; // Added AllergyRepository
    private final ObjectMapper objectMapper;

    private static List<MealDefinition> ALL_MEAL_DEFINITIONS = Collections.emptyList();
    private static List<IngredientDefinition> ALL_INGREDIENT_DEFINITIONS = Collections.emptyList();

    @Autowired
    public DataLoader(FoodRepository foodRepository,
                      RecipeRepository recipeRepository,
                      AllergyRepository allergyRepository, // Added to constructor
                      ObjectMapper objectMapper) {
        this.foodRepository = foodRepository;
        this.recipeRepository = recipeRepository;
        this.allergyRepository = allergyRepository; // Initialize AllergyRepository
        this.objectMapper = objectMapper;
    }

    // RecipeDetail class remains the same
    private static class RecipeDetail {
        final List<String> ingredientNames;
        final String instructions;
        final List<String> dietaryTags;
        final List<String> allergens;

        RecipeDetail(List<String> ingredientNames, String instructions, List<String> dietaryTags, List<String> allergens) {
            this.ingredientNames = ingredientNames != null ? Collections.unmodifiableList(new ArrayList<>(ingredientNames)) : Collections.emptyList();
            this.instructions = instructions;
            this.dietaryTags = dietaryTags != null ? Collections.unmodifiableList(new ArrayList<>(dietaryTags)) : Collections.emptyList();
            this.allergens = allergens != null ? Collections.unmodifiableList(new ArrayList<>(allergens)) : Collections.emptyList();
        }
    }

    @PostConstruct
    @Transactional
    public void loadData() {
        log.info("DataLoader: Initializing data loading sequence...");

        loadMealDefinitionsFromFile();
        loadIngredientDefinitionsFromFile();
        loadInitialAllergies(); // Call method to load allergies

        if (ALL_MEAL_DEFINITIONS.isEmpty() && foodRepository.count() == 0 && recipeRepository.count() == 0) {
            log.warn("Meal definitions (meals.json) are empty or unreadable, and repositories are empty. DataLoader cannot populate meals or recipes effectively.");
            if (ALL_INGREDIENT_DEFINITIONS.isEmpty()) {
                 log.warn("Ingredient definitions (ingredients.json) are also empty or unreadable.");
            }
            // Don't return early if allergies might still need loading
        }

        if (foodRepository.count() == 0) {
            log.info("Food repository is empty. Loading initial food items (from meal names and ingredient definitions).");
            loadInitialFoodItems();
        } else {
            log.info("Food repository already contains data ({} items). Checking for and adding missing ingredients only.", foodRepository.count());
            addMissingIngredientsOnly();
        }

        if (!ALL_MEAL_DEFINITIONS.isEmpty()) {
            log.info("Loading or updating recipes based on current meal definitions...");
            loadInitialRecipes();
        } else {
            log.warn("No meal definitions loaded from meals.json. Skipping recipe loading/updating.");
        }

        log.info("DataLoader: Data loading sequence finished.");
    }

    private void loadMealDefinitionsFromFile() {
        try (InputStream inputStream = new ClassPathResource("meals.json").getInputStream()) {
            ALL_MEAL_DEFINITIONS = objectMapper.readValue(inputStream, new TypeReference<List<MealDefinition>>() {});
            log.info("Successfully loaded {} meal definitions from meals.json", ALL_MEAL_DEFINITIONS.size());
            for (MealDefinition mealDef : ALL_MEAL_DEFINITIONS) {
                if (mealDef.getNutrition() == null) {
                    log.warn("MealDefinition '{}' is missing the 'nutrition' object. Nutritional data will be 0.", mealDef.getName());
                }
                if (mealDef.getBenefitDescription() == null || mealDef.getBenefitDescription().trim().isEmpty()) {
                    log.warn("MealDefinition '{}' is missing 'benefitDescription' or it is empty.", mealDef.getName());
                }
            }
        } catch (IOException e) {
            log.error("Failed to load meal definitions from meals.json. Error: {}", e.getMessage(), e);
            ALL_MEAL_DEFINITIONS = Collections.emptyList();
        }
    }

    private void loadIngredientDefinitionsFromFile() {
        try (InputStream inputStream = new ClassPathResource("ingredients.json").getInputStream()) {
            ALL_INGREDIENT_DEFINITIONS = objectMapper.readValue(inputStream, new TypeReference<List<IngredientDefinition>>() {});
            log.info("Successfully loaded {} ingredient definitions from ingredients.json", ALL_INGREDIENT_DEFINITIONS.size());
             for (IngredientDefinition ingDef : ALL_INGREDIENT_DEFINITIONS) {
                if (ingDef.getNutrition() == null) {
                    log.warn("IngredientDefinition '{}' is missing the 'nutrition' object. Nutritional data will be 0.", ingDef.getName());
                }
            }
        } catch (IOException e) {
            log.error("Failed to load ingredient definitions from ingredients.json. Error: {}", e.getMessage(), e);
            ALL_INGREDIENT_DEFINITIONS = Collections.emptyList();
        }
    }

    /**
     * Loads a predefined list of common allergies into the database if they don't already exist.
     */
    private void loadInitialAllergies() {
        log.info("Checking and loading initial allergies...");
        List<String> commonAllergyNames = Arrays.asList(
            "Peanuts", "Tree Nuts", "Milk", "Eggs", "Fish", "Shellfish",
            "Soy", "Wheat", "Gluten", "Sesame", "Mustard", "Celery", "Sulfites", "Corn", "Nightshades",
            // Add any other allergies you want to be available by default
            "Lupin", "Molluscs"
        );

        int count = 0;
        for (String allergyName : commonAllergyNames) {
            // Check if allergy already exists (case-insensitive check)
            if (allergyRepository.findByNameIgnoreCase(allergyName).isEmpty()) {
                allergyRepository.save(new Allergy(allergyName));
                count++;
            }
        }
        if (count > 0) {
            log.info("Loaded {} new common allergies into the database.", count);
        } else {
            log.info("All common allergies already exist in the database or no new allergies were defined to load.");
        }
    }


    private void loadInitialFoodItems() {
        log.info("Loading initial Food items (meals and ingredients) into the database...");
        List<Food> foodsToSave = new ArrayList<>();

        if (ALL_MEAL_DEFINITIONS.isEmpty()) {
            log.warn("No meal definitions loaded. Cannot create Food items for meal names.");
        } else {
            ALL_MEAL_DEFINITIONS.forEach(mealDef -> {
                if (mealDef.getName() != null && !mealDef.getName().trim().isEmpty()) {
                    NutritionInfo nutrition = mealDef.getNutrition();
                    if (nutrition != null) {
                        addFoodItem(foodsToSave, mealDef.getName().trim(),
                                nutrition.getCalories(), nutrition.getProtein(),
                                nutrition.getCarbs(), nutrition.getFat());
                    } else {
                        log.warn("MealDefinition for '{}' is missing nutrition. Creating Food item with 0 nutrition.", mealDef.getName());
                        addFoodItem(foodsToSave, mealDef.getName().trim(), 0, 0, 0, 0);
                    }
                } else {
                    log.warn("Skipping Food item creation for a MealDefinition due to missing name.");
                }
            });
        }

        addIngredientsToFoodList(foodsToSave);

        if (!foodsToSave.isEmpty()) {
            Map<String, Food> distinctFoodMap = new HashMap<>();
            for (Food food : foodsToSave) {
                distinctFoodMap.putIfAbsent(food.getName().toLowerCase(), food);
            }
            List<Food> distinctFoodsToSave = new ArrayList<>(distinctFoodMap.values());
            foodRepository.saveAll(distinctFoodsToSave);
            log.info("Saved {} distinct Food items to the database.", distinctFoodsToSave.size());
        } else {
            log.info("No new Food items (meals or ingredients) to save from initial load.");
        }
    }

    private void addMissingIngredientsOnly() {
        log.info("Checking for and adding missing INGREDIENT Food items to the database...");
        List<Food> ingredientsFromDefs = new ArrayList<>();
        addIngredientsToFoodList(ingredientsFromDefs);

        if (ingredientsFromDefs.isEmpty()) {
            log.info("No ingredient definitions loaded or all are invalid. Cannot check for missing ingredients.");
            return;
        }

        Set<String> existingFoodNamesLower = foodRepository.findAll().stream()
                                           .map(food -> food.getName().toLowerCase())
                                           .collect(Collectors.toSet());

        List<Food> newIngredientsToSave = ingredientsFromDefs.stream()
            .filter(definedIngredient -> !existingFoodNamesLower.contains(definedIngredient.getName().toLowerCase()))
            .collect(Collectors.toList());

        if (!newIngredientsToSave.isEmpty()) {
            foodRepository.saveAll(newIngredientsToSave);
            log.info("Added {} new INGREDIENT Food items to the database.", newIngredientsToSave.size());
        } else {
            log.info("No new missing ingredients found to add to the database.");
        }
    }

    private void addIngredientsToFoodList(List<Food> targetList) {
        if (ALL_INGREDIENT_DEFINITIONS.isEmpty()) {
            log.warn("No ingredient definitions loaded. Cannot add ingredients to the target food list.");
            return;
        }
        ALL_INGREDIENT_DEFINITIONS.forEach(ingDef -> {
            if (ingDef.getName() != null && !ingDef.getName().trim().isEmpty()) {
                NutritionInfo nutrition = ingDef.getNutrition();
                if (nutrition != null) {
                    addFoodItem(targetList, ingDef.getName().trim(),
                            nutrition.getCalories(), nutrition.getProtein(),
                            nutrition.getCarbs(), nutrition.getFat());
                } else {
                     log.warn("IngredientDefinition for '{}' is missing nutrition. Creating Food item with 0 nutrition.", ingDef.getName());
                     addFoodItem(targetList, ingDef.getName().trim(), 0,0,0,0);
                }
            } else {
                 log.warn("Skipping Food item creation for an IngredientDefinition due to missing name.");
            }
        });
    }

    private void addFoodItem(List<Food> list, String name, int cal, double p, double c, double f) {
        String trimmedName = name.trim();
        if (list.stream().noneMatch(food -> food.getName().equalsIgnoreCase(trimmedName))) {
            list.add(new Food(trimmedName, cal, p, c, f));
        } else {
            log.trace("Food item '{}' is already in the list to be processed this session. Skipping duplicate addition.", trimmedName);
        }
    }

    private void loadInitialRecipes() {
        if (ALL_MEAL_DEFINITIONS.isEmpty()) {
            log.warn("No meal definitions loaded from JSON. Cannot create or update recipes.");
            return;
        }

        List<Food> allIngredientsFromDb = foodRepository.findAll();
        Map<String, Food> ingredientLookupMap = allIngredientsFromDb.stream()
                .collect(Collectors.toMap(
                        food -> food.getName().trim().toLowerCase(),
                        food -> food,
                        (f1, f2) -> f1
                ));

        List<Recipe> recipesToSaveOrUpdate = new ArrayList<>();

        for (MealDefinition mealDef : ALL_MEAL_DEFINITIONS) {
            String currentMealName = mealDef.getName();
            if (currentMealName == null || currentMealName.trim().isEmpty()) {
                log.warn("Skipping a meal definition due to null or empty name.");
                continue;
            }
            final String finalMealName = currentMealName.trim();

            RecipeDetail recipeDetail = new RecipeDetail(
                mealDef.getIngredientNames(), mealDef.getInstructions(),
                mealDef.getDietaryTags(), mealDef.getAllergens()
            );
            List<Food> recipeFoodIngredients = findIngredientsByName(recipeDetail.ingredientNames, ingredientLookupMap, finalMealName);

            NutritionInfo nutrition = mealDef.getNutrition();
            if (nutrition == null) {
                log.warn("Recipe definition for '{}' is missing nutrition info. Nutritional values for Recipe entity will be default/zero.", finalMealName);
                nutrition = new NutritionInfo(0, 0, 0, 0);
            }

            Recipe recipe = recipeRepository.findByName(finalMealName)
                    .orElseGet(() -> {
                        log.info("Preparing NEW recipe for: {}", finalMealName);
                        return new Recipe();
                    });

            boolean needsUpdate = recipe.getId() == null;

            if (recipe.getId() == null) {
                 recipe.setName(finalMealName);
            }

            if (!Objects.equals(recipe.getInstructions(), recipeDetail.instructions)) {
                recipe.setInstructions(recipeDetail.instructions);
                needsUpdate = true;
            }

            Set<Long> currentRecipeFoodIds = recipe.getIngredients().stream().map(Food::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<Long> newRecipeFoodIds = recipeFoodIngredients.stream().map(Food::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!currentRecipeFoodIds.equals(newRecipeFoodIds)) {
                recipe.setIngredients(new ArrayList<>(recipeFoodIngredients));
                needsUpdate = true;
            }

            if (!new HashSet<>(recipe.getDietaryTags()).equals(new HashSet<>(recipeDetail.dietaryTags))) {
                recipe.setDietaryTags(new ArrayList<>(recipeDetail.dietaryTags));
                needsUpdate = true;
            }
            if (!new HashSet<>(recipe.getAllergens()).equals(new HashSet<>(recipeDetail.allergens))) {
                recipe.setAllergens(new HashSet<>(recipeDetail.allergens));
                needsUpdate = true;
            }

            if (!Objects.equals(recipe.getTotalCalories(), nutrition.getCalories())) {
                recipe.setTotalCalories(nutrition.getCalories()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getTotalProtein(), nutrition.getProtein())) {
                recipe.setTotalProtein(nutrition.getProtein()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getTotalCarbs(), nutrition.getCarbs())) {
                recipe.setTotalCarbs(nutrition.getCarbs()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getTotalFats(), nutrition.getFat())) {
                recipe.setTotalFats(nutrition.getFat()); needsUpdate = true;
            }

            if (!Objects.equals(recipe.getPrepTime(), mealDef.getPrepTime())) {
                recipe.setPrepTime(mealDef.getPrepTime()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getCookTime(), mealDef.getCookTime())) {
                recipe.setCookTime(mealDef.getCookTime()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getServings(), mealDef.getServings())) {
                recipe.setServings(mealDef.getServings()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getDifficulty(), mealDef.getDifficulty())) {
                recipe.setDifficulty(mealDef.getDifficulty()); needsUpdate = true;
            }
            if (!Objects.equals(recipe.getBenefitDescription(), mealDef.getBenefitDescription())) {
                recipe.setBenefitDescription(mealDef.getBenefitDescription());
                needsUpdate = true;
            }

            if(needsUpdate) {
                recipesToSaveOrUpdate.add(recipe);
                if(recipe.getId() == null) {
                     checkMissingIngredients(finalMealName, recipeDetail, recipeFoodIngredients);
                }
            }
        }

        if (!recipesToSaveOrUpdate.isEmpty()) {
            try {
                recipeRepository.saveAll(recipesToSaveOrUpdate);
                log.info("Saved/Updated {} recipes in the database.", recipesToSaveOrUpdate.size());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("Batch save failed due to data integrity violation (likely duplicate). Attempting to save individually...");
                for (Recipe r : recipesToSaveOrUpdate) {
                    try {
                        recipeRepository.save(r);
                    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                        log.warn("Skipping duplicate recipe: {}", r.getName());
                    } catch (Exception ex) {
                        log.error("Failed to save recipe '{}': {}", r.getName(), ex.getMessage());
                    }
                }
            }
        } else {
            log.info("No new recipes to create or existing recipes to update.");
        }
    }

    private void checkMissingIngredients(String mealName, RecipeDetail recipeDetail, List<Food> foundIngredients) {
        if (recipeDetail.ingredientNames == null || recipeDetail.ingredientNames.isEmpty()) {
            return;
        }
        if (foundIngredients.size() < recipeDetail.ingredientNames.size()) {
            Set<String> foundNamesLower = foundIngredients.stream()
                    .map(f -> f.getName().toLowerCase())
                    .collect(Collectors.toSet());
            List<String> missingNames = recipeDetail.ingredientNames.stream()
                    .filter(reqName -> reqName != null && !reqName.trim().isEmpty())
                    .map(String::trim)
                    .filter(reqNameTrimmed -> !foundNamesLower.contains(reqNameTrimmed.toLowerCase()))
                    .collect(Collectors.toList());

            if (!missingNames.isEmpty()) {
                 log.warn("Recipe '{}' processed with an incomplete ingredient list. Required: {}, Found: {}. Missing (ensure these are in ingredients.json and Food table): {}",
                         mealName,
                         recipeDetail.ingredientNames.size(),
                         foundIngredients.size(),
                         missingNames);
            }
        }
    }

    private List<Food> findIngredientsByName(List<String> names, Map<String, Food> ingredientLookupMap, String mealName) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<Food> foundIngredients = new ArrayList<>();
        Set<String> loggedMissingThisMeal = new HashSet<>();

        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                log.warn("Null or empty ingredient name found in definition for meal: {}", mealName);
                continue;
            }
            String lowerCaseName = name.trim().toLowerCase();
            Food food = ingredientLookupMap.get(lowerCaseName);
            if (food != null) {
                foundIngredients.add(food);
            } else {
                if (!loggedMissingThisMeal.contains(lowerCaseName)) {
                    log.warn("Ingredient '{}' for recipe '{}' not found in pre-fetched ingredient map. Ensure it's defined in ingredients.json and loaded into Food table.", name.trim(), mealName);
                    loggedMissingThisMeal.add(lowerCaseName);
                }
            }
        }
        return foundIngredients;
    }
}
