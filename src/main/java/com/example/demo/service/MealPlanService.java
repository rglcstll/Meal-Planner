package com.example.demo.service;

import com.example.demo.dto.MealPlanDTO;
import com.example.demo.model.MealPlan;
import com.example.demo.model.Recipe;
import com.example.demo.model.User;
import com.example.demo.repository.MealPlanRepository;
import com.example.demo.repository.RecipeRepository;
import com.example.demo.repository.UserRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MealPlanService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanService.class);

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AllergyService allergyService;

    @Autowired
    public MealPlanService(MealPlanRepository mealPlanRepository,
                           RecipeRepository recipeRepository,
                           UserRepository userRepository,
                           ObjectMapper objectMapper,
                           AllergyService allergyService) {
        this.mealPlanRepository = mealPlanRepository;
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.allergyService = allergyService;
    }

    // ... (generateMealPlanWithAllergyFiltering, getRecipesForDiet, generateFilteredMealPlan, etc. remain as they were in mealplanservice_java_updated_v2) ...
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> generateMealPlanWithAllergyFiltering(String dietType, String userIdString) {
        log.info("--- Attempting to generate meal plan for diet: {} with allergy filtering for user ID string: {} ---", dietType, userIdString);

        List<Recipe> suitableRecipes = getRecipesForDiet(dietType);
        log.info("Step 1: Found {} recipes suitable for diet: {}", suitableRecipes.size(), dietType);

        if (suitableRecipes.isEmpty()) {
            log.warn("No suitable recipes found for diet type: {}. Returning empty meal plan.", dietType);
            return Collections.emptyMap();
        }

        List<Recipe> allergyFilteredRecipes = allergyService.filterRecipesByAllergies(suitableRecipes, userIdString);
        log.info("Step 2: After allergy filtering for user string ID '{}', {} recipes remain", userIdString, allergyFilteredRecipes.size());

        if (allergyFilteredRecipes.isEmpty()) {
            log.warn("No recipes remain after allergy filtering for user string ID: {}. Returning empty meal plan.", userIdString);
            return createEmptyPlanMap("No suitable meals for your diet and allergies."); // Use helper
        }

        log.info("Step 3: Separating allergy-filtered recipes by meal type...");
        List<Recipe> breakfastOptions = filterRecipesByMealType(allergyFilteredRecipes, "breakfast");
        List<Recipe> lunchOptions = filterRecipesByMealType(allergyFilteredRecipes, "lunch");
        List<Recipe> dinnerOptions = filterRecipesByMealType(allergyFilteredRecipes, "dinner");
        log.info("Meal type counts AFTER filtering for diet '{}' and allergies: Breakfast={}, Lunch={}, Dinner={}",
                 dietType, breakfastOptions.size(), lunchOptions.size(), dinnerOptions.size());

        if (breakfastOptions.isEmpty() && lunchOptions.isEmpty() && dinnerOptions.isEmpty() && !allergyFilteredRecipes.isEmpty()) {
            log.warn("Although {} suitable recipes found after allergy filtering, none matched specific meal type patterns. Using all filtered recipes as fallback for each meal type.",
                     allergyFilteredRecipes.size());
            breakfastOptions = new ArrayList<>(allergyFilteredRecipes);
            lunchOptions = new ArrayList<>(allergyFilteredRecipes);
            dinnerOptions = new ArrayList<>(allergyFilteredRecipes);
        } else if (breakfastOptions.isEmpty() || lunchOptions.isEmpty() || dinnerOptions.isEmpty()){
            log.warn("One or more meal type categories (Breakfast, Lunch, or Dinner) are empty after filtering. The generated plan might have default messages for these slots.");
        }

        log.info("Step 4: Building the meal plan map...");
        Map<String, Map<String, String>> mealPlan = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        Random random = new Random();

        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            dayMeals.put("BREAKFAST", getRandomRecipeName(breakfastOptions, random, "No suitable breakfast for your allergies"));
            dayMeals.put("LUNCH", getRandomRecipeName(lunchOptions, random, "No suitable lunch for your allergies"));
            dayMeals.put("DINNER", getRandomRecipeName(dinnerOptions, random, "No suitable dinner for your allergies"));
            mealPlan.put(day, dayMeals);
        }

        log.info("--- Successfully generated allergy-filtered meal plan for diet: {} and user string ID: {} ---", dietType, userIdString);
        return mealPlan;
    }

    private List<Recipe> getRecipesForDiet(String dietType) {
        final String lowerCaseDietType = dietType.toLowerCase();
        if ("standard".equalsIgnoreCase(lowerCaseDietType) || "balanced".equalsIgnoreCase(lowerCaseDietType) || lowerCaseDietType.isEmpty()) {
            log.debug("Diet is '{}' or empty, returning all recipes.", lowerCaseDietType);
            return recipeRepository.findAll();
        } else {
            // Optimized: Use database query to fetch only matching recipes
            List<Recipe> dietFilteredRecipes = recipeRepository.findByDietaryTag(lowerCaseDietType);
            log.debug("Filtered for diet '{}' via DB, found {} recipes.", dietType, dietFilteredRecipes.size());
            return dietFilteredRecipes;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> generateFilteredMealPlan(String dietType) {
        log.info("--- Attempting to generate meal plan for diet: {} (no allergy filtering) ---", dietType);
        List<Recipe> suitableRecipes = getRecipesForDiet(dietType);

        if (suitableRecipes.isEmpty()) {
            log.warn("No suitable recipes found specifically for diet type: {}. Returning empty meal plan.", dietType);
            return createEmptyPlanMap("No suitable meals for this diet."); // Use helper
        }

        log.info("Separating diet-suitable recipes by meal type...");
        List<Recipe> breakfastOptions = filterRecipesByMealType(suitableRecipes, "breakfast");
        List<Recipe> lunchOptions = filterRecipesByMealType(suitableRecipes, "lunch");
        List<Recipe> dinnerOptions = filterRecipesByMealType(suitableRecipes, "dinner");
        log.info("Meal type counts for diet '{}': Breakfast={}, Lunch={}, Dinner={}",
                 dietType, breakfastOptions.size(), lunchOptions.size(), dinnerOptions.size());

        if (breakfastOptions.isEmpty() && lunchOptions.isEmpty() && dinnerOptions.isEmpty() && !suitableRecipes.isEmpty()) {
            log.warn("Although {} suitable recipes found for diet '{}', none matched specific meal type patterns. Using all suitable recipes as fallback for each meal type.",
                     suitableRecipes.size(), dietType);
            breakfastOptions = new ArrayList<>(suitableRecipes);
            lunchOptions = new ArrayList<>(suitableRecipes);
            dinnerOptions = new ArrayList<>(suitableRecipes);
        } else if (breakfastOptions.isEmpty() || lunchOptions.isEmpty() || dinnerOptions.isEmpty()){
             log.warn("One or more meal type categories (Breakfast, Lunch, or Dinner) are empty after filtering for diet '{}'. Plan might have defaults.", dietType);
        }

        Map<String, Map<String, String>> mealPlan = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        Random random = new Random();

        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            dayMeals.put("BREAKFAST", getRandomRecipeName(breakfastOptions, random, "No suitable breakfast"));
            dayMeals.put("LUNCH", getRandomRecipeName(lunchOptions, random, "No suitable lunch"));
            dayMeals.put("DINNER", getRandomRecipeName(dinnerOptions, random, "No suitable dinner"));
            mealPlan.put(day, dayMeals);
        }
        log.info("--- Successfully generated meal plan map for diet: {} ---", dietType);
        return mealPlan;
    }

    private List<Recipe> filterRecipesByMealType(List<Recipe> recipes, String mealTypeStr) {
        com.example.demo.model.MealType mealType = com.example.demo.model.MealType.fromString(mealTypeStr);
        if (mealType == null) {
            log.warn("Invalid meal type string: '{}'. Returning all recipes as fallback.", mealTypeStr);
            return recipes;
        }

        log.debug("Filtering {} recipes by meal type: '{}'", recipes.size(), mealType);
        
        List<Recipe> filtered = recipes.stream().filter(recipe -> {
            // First check tags
            if (recipe.getDietaryTags() != null) {
                for (String tag : recipe.getDietaryTags()) {
                    if (tag.equalsIgnoreCase(mealType.name())) {
                        return true;
                    }
                }
            }
            
            // Fallback to name guessing
            String name = recipe.getName().toLowerCase();
            switch (mealType) {
                case BREAKFAST: return name.contains("eggs") || name.contains("oatmeal") || name.contains("breakfast") || name.contains("pancake") || name.contains("yogurt") || name.contains("smoothie") || name.contains("toast") || name.contains("cereal") || name.contains("granola") || name.contains("muffin");
                case LUNCH: return name.contains("salad") || name.contains("sandwich") || name.contains("wrap") || name.contains("soup") || name.contains("quinoa") || name.contains("hummus") || name.contains("bowl") || name.contains("platter") || name.contains("leftover");
                case DINNER: return !name.contains("breakfast") && !name.contains("cereal") && !name.contains("toast") && !name.contains("sandwich") && !name.contains("wrap") && !name.contains("smoothie") && !name.contains("yogurt") && !name.contains("oatmeal") && !name.contains("pancake") && !name.contains("parfait") && !name.contains("granola");
                default: return true;
            }
        }).collect(Collectors.toList());
        log.trace("Finished filtering for type '{}'. {} recipes remain.", mealType, filtered.size());
        return filtered;
    }

    private String getRandomRecipeName(List<Recipe> recipeList, Random random, String defaultName) {
         if (recipeList == null || recipeList.isEmpty()) {
            log.warn("Recipe list for selection is empty, returning default: '{}'", defaultName);
            return defaultName;
         }
         int randomIndex = random.nextInt(recipeList.size());
         String selectedName = recipeList.get(randomIndex).getName();
         log.trace("Randomly selected '{}' from list of size {}", selectedName, recipeList.size());
         return selectedName;
    }

    @Transactional(readOnly = true)
    public MealPlanDTO getMealPlanForDate(LocalDate date) {
        log.info("Fetching saved meal plan for date: {}", date);
        Optional<MealPlan> mealPlanOpt = mealPlanRepository.findByDate(date);

        if (mealPlanOpt.isEmpty()) {
            log.warn("No saved meal plan found for date: {}", date);
            return null;
        }
        MealPlan mealPlan = mealPlanOpt.get();
        log.debug("Found saved meal plan with ID: {}", mealPlan.getId());
        return convertEntityToDto(mealPlan);
    }

    @Transactional
    public MealPlanDTO saveOrUpdate(MealPlanDTO mealPlanDto) {
        log.info("Service: Attempting to save/update meal plan for date: {}", mealPlanDto.getDate());

        if (mealPlanDto == null || mealPlanDto.getDate() == null || mealPlanDto.getDate().isBlank()) {
            log.error("MealPlanDTO and Date are required to save a meal plan. DTO: {}", mealPlanDto);
            throw new IllegalArgumentException("MealPlanDTO and Date are required to save a meal plan.");
        }
        LocalDate planDate;
        try {
            planDate = LocalDate.parse(mealPlanDto.getDate());
        } catch (DateTimeParseException e) {
            log.error("Invalid date format received: {}. Expected yyyy-MM-dd.", mealPlanDto.getDate(), e);
            throw new IllegalArgumentException("Invalid date format. Please use yyyy-MM-dd.");
        }

        User userEntity = null;
        Long userIdFromDto = mealPlanDto.getUserId(); // Assuming MealPlanDTO.getUserId() returns Long

        if (userIdFromDto != null) {
            userEntity = userRepository.findById(userIdFromDto)
                    .orElseThrow(() -> {
                        log.error("User not found with ID: {} for saving meal plan.", userIdFromDto);
                        return new RuntimeException("User not found with ID: " + userIdFromDto + " for saving meal plan.");
                    });
        } else {
            log.warn("No valid userId provided in MealPlanDTO (userId is null). Saving plan without specific user association.");
        }

        final User finalUserEntity = userEntity;

        Optional<MealPlan> existingPlanOpt = mealPlanRepository.findByDate(planDate)
            .filter(mp -> {
                if (finalUserEntity != null) {
                    return mp.getUser() != null && finalUserEntity.getId().equals(mp.getUser().getId());
                } else {
                    return mp.getUser() == null;
                }
            });

        MealPlan mealPlanEntity;
        if (existingPlanOpt.isPresent()) {
            log.debug("Found existing meal plan for date {} (and user if applicable). Updating.", planDate);
            mealPlanEntity = existingPlanOpt.get();
            if (mealPlanEntity.getMeals() != null) {
                mealPlanEntity.getMeals().clear();
            } else {
                mealPlanEntity.setMeals(new ArrayList<>());
            }
        } else {
            log.debug("No existing meal plan found for date {} (and user if applicable). Creating new.", planDate);
            mealPlanEntity = new MealPlan();
            mealPlanEntity.setDate(planDate);
            mealPlanEntity.setMeals(new ArrayList<>());
        }

        mealPlanEntity.setUser(finalUserEntity);

        Map<String, Map<String, String>> mealsMapFromDto = mealPlanDto.getMeals();
        Set<Recipe> recipesToAssociate = new HashSet<>();

        if (mealsMapFromDto != null && !mealsMapFromDto.isEmpty()) {
            for (Map.Entry<String, Map<String, String>> dayEntry : mealsMapFromDto.entrySet()) {
                for (Map.Entry<String, String> mealEntry : dayEntry.getValue().entrySet()) {
                    String recipeName = mealEntry.getValue();
                    if (recipeName != null && !recipeName.isBlank() && !recipeName.toLowerCase().contains("no suitable")) {
                        recipeRepository.findByName(recipeName).ifPresentOrElse(
                            recipesToAssociate::add,
                            () -> log.warn("Recipe named '{}' not found in repository. Skipping association for save.", recipeName)
                        );
                    }
                }
            }
            mealPlanEntity.setMeals(new ArrayList<>(recipesToAssociate));
            log.debug("Associated {} unique Recipe entities to meal plan.", recipesToAssociate.size());

            try {
                String mealsJsonString = objectMapper.writeValueAsString(mealsMapFromDto);
                mealPlanEntity.setMealsJson(mealsJsonString);
                log.debug("Serialized meals map to JSON for storage: {}...", mealsJsonString.substring(0, Math.min(100, mealsJsonString.length())));
            } catch (JsonProcessingException e) {
                log.error("Error serializing meals map to JSON for date {}: {}", planDate, e.getMessage(), e);
                throw new RuntimeException("Error processing meal plan data for saving.", e);
            }
        } else {
            log.warn("MealPlanDTO received with null or empty meals map for date {}. Plan will have no structured meals.", planDate);
            mealPlanEntity.setMeals(new ArrayList<>());
            mealPlanEntity.setMealsJson("[]");
        }

        try {
            MealPlan savedEntity = mealPlanRepository.save(mealPlanEntity);
            log.info("Successfully saved/updated MealPlan entity with ID: {} for date {}", savedEntity.getId(), savedEntity.getDate());
            return convertEntityToDto(savedEntity);
        } catch (DataAccessException e) {
            log.error("Database error during repository save operation for MealPlan on date {}: {}", planDate, e.getMessage(), e);
            throw new RuntimeException("Failed to save meal plan due to a database error. Please check for constraint violations or data issues.", e);
        } catch (RuntimeException e) { // Catch specific RuntimeExceptions (like User not found)
            throw e; // Re-throw the original RuntimeException
        } catch (Exception e) { // General unexpected errors
            log.error("An unexpected error occurred during repository save operation for MealPlan on date {}: {}", planDate, e.getMessage(), e);
            throw new RuntimeException("An unexpected error occurred while saving the meal plan.", e);
        }
    }

    private MealPlanDTO convertEntityToDto(MealPlan entity) {
        if (entity == null) {
            return null;
        }
        MealPlanDTO dto = new MealPlanDTO();
        dto.setId(entity.getId());
        if (entity.getDate() != null) {
            dto.setDate(entity.getDate().toString());
        }

        if (entity.getUser() != null && entity.getUser().getId() != null) {
            dto.setUserId(entity.getUser().getId());
        } else {
            dto.setUserId(null);
        }

        Map<String, Map<String, String>> mealsMap = new HashMap<>();
        if (entity.getMealsJson() != null && !entity.getMealsJson().isEmpty()) {
            try {
                mealsMap = objectMapper.readValue(
                    entity.getMealsJson(),
                    new TypeReference<Map<String, Map<String, String>>>() {}
                );
            } catch (JsonProcessingException e) {
                log.error("Error deserializing mealsJson to Map for MealPlan ID {}: {}. Returning empty meals map.",
                          entity.getId(), e.getMessage(), e);
                mealsMap = new HashMap<>();
            }
        }
        dto.setMeals(mealsMap);

        log.debug("Converted MealPlan entity (ID: {}) to DTO for date {}", entity.getId(), dto.getDate());
        return dto;
    }

    /**
     * This method is called by MealPlanController for diet-specific generation
     * without allergy filtering. It now correctly returns a Map.
     * The weightGoal parameter is logged but not actively used in the current filtering logic.
     */
    public Map<String, Map<String, String>> generateMealPlan(String dietType, String weightGoal) {
        log.warn("generateMealPlan(dietType, weightGoal) called. WeightGoal ('{}') is currently logged but not used in filtering. Using generateFilteredMealPlan(dietType).", weightGoal);
        return generateFilteredMealPlan(dietType); // This returns Map<String, Map<String, String>>
    }

    // ==========================================================================
    // Calorie Calculation Methods (Mifflin-St Jeor Equation)
    // ==========================================================================

    /**
     * Calculates Basal Metabolic Rate (BMR) using Mifflin-St Jeor equation.
     * Male: (10 × weight_kg) + (6.25 × height_cm) - (5 × age) + 5
     * Female: (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 161
     */
    private double calculateBMR(User user) {
        if (user == null || user.getWeight() == null || user.getHeight() == null || user.getAge() == null) {
            log.warn("Cannot calculate BMR: user data incomplete. Using default BMR of 1800.");
            return 1800.0;
        }

        double weight = user.getWeight();
        double height = user.getHeight();
        int age = user.getAge();
        String gender = user.getGender() != null ? user.getGender().toLowerCase() : "male";

        double bmr;
        if ("female".equals(gender) || "f".equals(gender)) {
            bmr = (10 * weight) + (6.25 * height) - (5 * age) - 161;
        } else {
            bmr = (10 * weight) + (6.25 * height) - (5 * age) + 5;
        }

        log.debug("Calculated BMR for user (weight={}, height={}, age={}, gender={}): {}",
                  weight, height, age, gender, bmr);
        return bmr;
    }

    /**
     * Gets activity multiplier based on activity level.
     * Sedentary: 1.2, Light: 1.375, Moderate: 1.55, Active: 1.725, Very Active: 1.9
     */
    private double getActivityMultiplier(String activityLevel) {
        if (activityLevel == null) {
            return 1.55; // Default to moderate
        }

        String level = activityLevel.toLowerCase().trim();
        switch (level) {
            case "sedentary":
            case "inactive":
                return 1.2;
            case "light":
            case "lightly active":
            case "lightly_active":
                return 1.375;
            case "moderate":
            case "moderately active":
            case "moderately_active":
                return 1.55;
            case "active":
            case "very active":
                return 1.725;
            case "extra active":
            case "extra_active":
            case "very_active":
                return 1.9;
            default:
                log.debug("Unknown activity level '{}', using moderate (1.55)", activityLevel);
                return 1.55;
        }
    }

    /**
     * Calculates Total Daily Energy Expenditure (TDEE).
     * TDEE = BMR × Activity Multiplier
     */
    private double calculateTDEE(User user) {
        double bmr = calculateBMR(user);
        double multiplier = getActivityMultiplier(user.getActivityLevel());
        double tdee = bmr * multiplier;
        log.debug("Calculated TDEE: BMR({}) × Multiplier({}) = {}", bmr, multiplier, tdee);
        return tdee;
    }

    /**
     * Calculates daily calorie target based on TDEE and weight goal.
     * Lose weight: TDEE - 500 calories
     * Maintain: TDEE
     * Gain weight: TDEE + 500 calories
     */
    private int calculateDailyCalorieTarget(User user, String weightGoal) {
        double tdee = calculateTDEE(user);
        int target;

        String goal = weightGoal != null ? weightGoal.toLowerCase().trim() : "maintain";

        switch (goal) {
            case "lose":
            case "lose weight":
            case "weight_loss":
            case "cut":
                target = (int) Math.round(tdee - 500);
                break;
            case "gain":
            case "gain weight":
            case "weight_gain":
            case "bulk":
                target = (int) Math.round(tdee + 500);
                break;
            case "maintain":
            case "maintenance":
            default:
                target = (int) Math.round(tdee);
                break;
        }

        // Ensure minimum healthy calorie intake
        target = Math.max(target, 1200);
        log.info("Daily calorie target for goal '{}': {} calories (TDEE: {})", goal, target, tdee);
        return target;
    }

    /**
     * Gets the currently authenticated user from the security context.
     */
    private User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                return userRepository.findByEmail(email);
            }
        } catch (Exception e) {
            log.warn("Could not get current user from security context: {}", e.getMessage());
        }
        return null;
    }

    // ==========================================================================
    // Meal Plan Generation Methods
    // ==========================================================================

    /**
     * Generates a basic meal plan based on weight goal and user's calorie needs.
     * Selects meals that fit within the daily calorie target.
     */
    @Transactional(readOnly = true)
    public MealPlanDTO generateBasicMealPlanStub(String weightGoal) {
        log.info("generateBasicMealPlanStub called with weightGoal='{}'", weightGoal);

        User currentUser = getCurrentUser();
        int dailyCalorieTarget;

        if (currentUser != null) {
            dailyCalorieTarget = calculateDailyCalorieTarget(currentUser, weightGoal);
            log.info("Generating meal plan for user '{}' with calorie target: {}",
                     currentUser.getEmail(), dailyCalorieTarget);
        } else {
            // Default calorie target if no user is logged in
            dailyCalorieTarget = 2000;
            if ("lose".equalsIgnoreCase(weightGoal)) dailyCalorieTarget = 1500;
            else if ("gain".equalsIgnoreCase(weightGoal)) dailyCalorieTarget = 2500;
            log.info("No authenticated user found. Using default calorie target: {}", dailyCalorieTarget);
        }

        // Get all recipes and categorize by meal type
        List<Recipe> allRecipes = recipeRepository.findAll();
        if (allRecipes.isEmpty()) {
            log.warn("No recipes found in database. Returning empty meal plan.");
            return createEmptyMealPlanDTO("No recipes available.");
        }

        List<Recipe> breakfastOptions = filterRecipesByMealType(allRecipes, "breakfast");
        List<Recipe> lunchOptions = filterRecipesByMealType(allRecipes, "lunch");
        List<Recipe> dinnerOptions = filterRecipesByMealType(allRecipes, "dinner");

        // If no specific meal types found, use all recipes as fallback
        if (breakfastOptions.isEmpty()) breakfastOptions = new ArrayList<>(allRecipes);
        if (lunchOptions.isEmpty()) lunchOptions = new ArrayList<>(allRecipes);
        if (dinnerOptions.isEmpty()) dinnerOptions = new ArrayList<>(allRecipes);

        // Calculate target calories per meal (roughly: breakfast 25%, lunch 35%, dinner 40%)
        int breakfastCalorieTarget = (int) (dailyCalorieTarget * 0.25);
        int lunchCalorieTarget = (int) (dailyCalorieTarget * 0.35);
        int dinnerCalorieTarget = (int) (dailyCalorieTarget * 0.40);

        // Generate 7-day meal plan
        Map<String, Map<String, String>> mealsMap = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        Random random = new Random();

        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            dayMeals.put("BREAKFAST", selectMealByCalories(breakfastOptions, breakfastCalorieTarget, random));
            dayMeals.put("LUNCH", selectMealByCalories(lunchOptions, lunchCalorieTarget, random));
            dayMeals.put("DINNER", selectMealByCalories(dinnerOptions, dinnerCalorieTarget, random));
            mealsMap.put(day, dayMeals);
        }

        MealPlanDTO dto = new MealPlanDTO();
        dto.setDate(LocalDate.now().toString());
        dto.setMeals(mealsMap);
        if (currentUser != null) {
            dto.setUserId(currentUser.getId());
        }

        log.info("Successfully generated basic meal plan with {} days", days.length);
        return dto;
    }

    /**
     * Generates a custom meal plan with specified number of meals per day.
     */
    @Transactional(readOnly = true)
    public MealPlanDTO generateCustomMealPlanStub(int mealsPerDay, String weightGoal) {
        log.info("generateCustomMealPlanStub called with mealsPerDay={}, weightGoal='{}'", mealsPerDay, weightGoal);

        // Validate meals per day (2-6 is reasonable)
        mealsPerDay = Math.max(2, Math.min(6, mealsPerDay));

        User currentUser = getCurrentUser();
        int dailyCalorieTarget;

        if (currentUser != null) {
            dailyCalorieTarget = calculateDailyCalorieTarget(currentUser, weightGoal);
        } else {
            dailyCalorieTarget = 2000;
            if ("lose".equalsIgnoreCase(weightGoal)) dailyCalorieTarget = 1500;
            else if ("gain".equalsIgnoreCase(weightGoal)) dailyCalorieTarget = 2500;
        }

        // Get all recipes
        List<Recipe> allRecipes = recipeRepository.findAll();
        if (allRecipes.isEmpty()) {
            return createEmptyMealPlanDTO("No recipes available.");
        }

        // Categorize recipes
        List<Recipe> breakfastOptions = filterRecipesByMealType(allRecipes, "breakfast");
        List<Recipe> lunchOptions = filterRecipesByMealType(allRecipes, "lunch");
        List<Recipe> dinnerOptions = filterRecipesByMealType(allRecipes, "dinner");
        List<Recipe> snackOptions = new ArrayList<>(allRecipes); // Use all for snacks

        if (breakfastOptions.isEmpty()) breakfastOptions = new ArrayList<>(allRecipes);
        if (lunchOptions.isEmpty()) lunchOptions = new ArrayList<>(allRecipes);
        if (dinnerOptions.isEmpty()) dinnerOptions = new ArrayList<>(allRecipes);

        // Calculate calories per meal
        int caloriesPerMeal = dailyCalorieTarget / mealsPerDay;

        // Define meal types based on number of meals
        String[] mealTypes = getMealTypesForCount(mealsPerDay);

        // Generate 7-day meal plan
        Map<String, Map<String, String>> mealsMap = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        Random random = new Random();

        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            for (String mealType : mealTypes) {
                List<Recipe> options;
                switch (mealType.toUpperCase()) {
                    case "BREAKFAST":
                        options = breakfastOptions;
                        break;
                    case "LUNCH":
                        options = lunchOptions;
                        break;
                    case "DINNER":
                        options = dinnerOptions;
                        break;
                    default: // SNACK1, SNACK2, etc.
                        options = snackOptions;
                        break;
                }
                dayMeals.put(mealType.toUpperCase(), selectMealByCalories(options, caloriesPerMeal, random));
            }
            mealsMap.put(day, dayMeals);
        }

        MealPlanDTO dto = new MealPlanDTO();
        dto.setDate(LocalDate.now().toString());
        dto.setMeals(mealsMap);
        if (currentUser != null) {
            dto.setUserId(currentUser.getId());
        }

        log.info("Successfully generated custom meal plan with {} meals/day for {} days", mealsPerDay, days.length);
        return dto;
    }

    /**
     * Returns meal type names based on number of meals per day.
     */
    private String[] getMealTypesForCount(int mealsPerDay) {
        switch (mealsPerDay) {
            case 2:
                return new String[]{"BREAKFAST", "DINNER"};
            case 3:
                return new String[]{"BREAKFAST", "LUNCH", "DINNER"};
            case 4:
                return new String[]{"BREAKFAST", "LUNCH", "SNACK", "DINNER"};
            case 5:
                return new String[]{"BREAKFAST", "SNACK1", "LUNCH", "SNACK2", "DINNER"};
            case 6:
                return new String[]{"BREAKFAST", "SNACK1", "LUNCH", "SNACK2", "DINNER", "SNACK3"};
            default:
                return new String[]{"BREAKFAST", "LUNCH", "DINNER"};
        }
    }

    /**
     * Selects a meal from the options that best fits the calorie target.
     */
    private String selectMealByCalories(List<Recipe> options, int targetCalories, Random random) {
        if (options == null || options.isEmpty()) {
            return "No suitable meal available";
        }

        // Find recipes within 30% of target calories
        int minCalories = (int) (targetCalories * 0.7);
        int maxCalories = (int) (targetCalories * 1.3);

        List<Recipe> suitableRecipes = options.stream()
            .filter(r -> r.getTotalCalories() != null &&
                        r.getTotalCalories() >= minCalories &&
                        r.getTotalCalories() <= maxCalories)
            .collect(Collectors.toList());

        // If no recipes in range, use closest ones
        if (suitableRecipes.isEmpty()) {
            suitableRecipes = options.stream()
                .filter(r -> r.getTotalCalories() != null && r.getTotalCalories() > 0)
                .sorted(Comparator.comparingInt(r -> Math.abs(r.getTotalCalories() - targetCalories)))
                .limit(5)
                .collect(Collectors.toList());
        }

        // If still empty, just use any recipe
        if (suitableRecipes.isEmpty()) {
            suitableRecipes = new ArrayList<>(options);
        }

        // Select random recipe from suitable options
        int index = random.nextInt(suitableRecipes.size());
        return suitableRecipes.get(index).getName();
    }

    /**
     * Creates an empty meal plan DTO with a default message.
     */
    private MealPlanDTO createEmptyMealPlanDTO(String message) {
        MealPlanDTO dto = new MealPlanDTO();
        dto.setDate(LocalDate.now().toString());
        Map<String, Map<String, String>> meals = createEmptyPlanMap(message);
        dto.setMeals(meals);
        return dto;
    }
    
    private Map<String, Map<String, String>> createEmptyPlanMap(String defaultMessage) {
        Map<String, Map<String, String>> emptyPlan = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            dayMeals.put("BREAKFAST", defaultMessage);
            dayMeals.put("LUNCH", defaultMessage);
            dayMeals.put("DINNER", defaultMessage);
            emptyPlan.put(day.toUpperCase(), dayMeals);
        }
        log.debug("Created empty plan map structure with default message: {}", defaultMessage);
        return emptyPlan;
    }
}
