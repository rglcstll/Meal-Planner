package com.example.demo.service;

import com.example.demo.dto.ShoppingListDTO;
import com.example.demo.dto.ShoppingListDTO.ShoppingItem;
import com.example.demo.model.Food;
import com.example.demo.model.MealPlan;
import com.example.demo.model.Recipe;
import com.example.demo.repository.MealPlanRepository;
import com.example.demo.repository.RecipeRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for generating shopping lists from meal plans.
 * Aggregates ingredients, groups by category, and combines duplicates.
 */
@Service
public class ShoppingListService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListService.class);

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final AllergyService allergyService;
    private final ObjectMapper objectMapper;

    // Category patterns for ingredient classification
    private static final Map<String, Pattern> CATEGORY_PATTERNS = new LinkedHashMap<>();

    static {
        // Order matters - more specific patterns first
        CATEGORY_PATTERNS.put("produce", Pattern.compile(
            "\\b(apple|banana|orange|lemon|lime|tomato|potato|onion|garlic|ginger|carrot|celery|broccoli|" +
            "spinach|lettuce|kale|cabbage|pepper|cucumber|zucchini|squash|mushroom|avocado|berry|berries|" +
            "strawberry|blueberry|raspberry|grape|mango|pineapple|melon|watermelon|peach|pear|plum|" +
            "cherry|corn|peas|beans|green bean|asparagus|artichoke|eggplant|beet|radish|turnip|" +
            "parsley|cilantro|basil|mint|dill|chive|scallion|leek|shallot|vegetable|fruit|salad|greens)\\b",
            Pattern.CASE_INSENSITIVE));

        CATEGORY_PATTERNS.put("meat", Pattern.compile(
            "\\b(chicken|beef|pork|fish|shrimp|salmon|tuna|cod|tilapia|turkey|bacon|sausage|lamb|" +
            "steak|sirloin|ground|meat|ribeye|tenderloin|breast|thigh|drumstick|wing|ham|prosciutto|" +
            "seafood|crab|lobster|scallop|mussel|clam|oyster|anchovy|sardine|mackerel|trout)\\b",
            Pattern.CASE_INSENSITIVE));

        CATEGORY_PATTERNS.put("dairy", Pattern.compile(
            "\\b(milk|cheese|butter|yogurt|cream|egg|eggs|feta|parmesan|cheddar|mozzarella|swiss|" +
            "brie|gouda|ricotta|cottage|sour cream|whipped cream|half and half|buttermilk|ghee|" +
            "cream cheese|mascarpone)\\b",
            Pattern.CASE_INSENSITIVE));

        CATEGORY_PATTERNS.put("bakery", Pattern.compile(
            "\\b(bread|roll|bun|bagel|muffin|cake|pastry|toast|crouton|wrap|tortilla|pita|croissant|" +
            "dough|biscuit|scone|brioche|focaccia|naan|flatbread|sourdough|ciabatta|baguette)\\b",
            Pattern.CASE_INSENSITIVE));

        CATEGORY_PATTERNS.put("pantry", Pattern.compile(
            "\\b(rice|pasta|flour|sugar|salt|pepper|spice|oil|olive oil|vinegar|sauce|soy|" +
            "broth|stock|cereal|oat|oatmeal|granola|honey|syrup|maple|cornstarch|baking|" +
            "bean|lentil|chickpea|nut|almond|walnut|pecan|cashew|peanut|seed|quinoa|couscous|" +
            "noodle|condiment|dressing|jam|jelly|peanut butter|almond butter|canned|jar|" +
            "oregano|thyme|rosemary|cumin|paprika|cinnamon|nutmeg|vanilla|cocoa|chocolate|" +
            "coconut|olive|mayonnaise|mustard|ketchup|salsa|hot sauce|sriracha|teriyaki)\\b",
            Pattern.CASE_INSENSITIVE));
    }

    @Autowired
    public ShoppingListService(MealPlanRepository mealPlanRepository,
                               RecipeRepository recipeRepository,
                               AllergyService allergyService,
                               ObjectMapper objectMapper) {
        this.mealPlanRepository = mealPlanRepository;
        this.recipeRepository = recipeRepository;
        this.allergyService = allergyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a shopping list from a meal plan by its ID.
     */
    @Transactional(readOnly = true)
    public ShoppingListDTO generateShoppingListByMealPlanId(Long mealPlanId) {
        log.info("Generating shopping list for meal plan ID: {}", mealPlanId);

        Optional<MealPlan> mealPlanOpt = mealPlanRepository.findById(mealPlanId);
        if (mealPlanOpt.isEmpty()) {
            log.warn("Meal plan not found with ID: {}", mealPlanId);
            return createEmptyShoppingList(mealPlanId, "Meal plan not found");
        }

        return generateShoppingListFromMealPlan(mealPlanOpt.get());
    }

    /**
     * Generates a shopping list from a meal plan by its date.
     */
    @Transactional(readOnly = true)
    public ShoppingListDTO generateShoppingListByDate(LocalDate date) {
        log.info("Generating shopping list for meal plan date: {}", date);

        Optional<MealPlan> mealPlanOpt = mealPlanRepository.findByDate(date);
        if (mealPlanOpt.isEmpty()) {
            log.warn("Meal plan not found for date: {}", date);
            return createEmptyShoppingList(null, "No meal plan for this date");
        }

        return generateShoppingListFromMealPlan(mealPlanOpt.get());
    }

    /**
     * Generates a shopping list directly from a meals map (for unsaved plans).
     */
    @Transactional(readOnly = true)
    public ShoppingListDTO generateShoppingListFromMealsMap(Map<String, Map<String, String>> mealsMap) {
        log.info("Generating shopping list from meals map");

        if (mealsMap == null || mealsMap.isEmpty()) {
            return createEmptyShoppingList(null, "No meals provided");
        }

        // Collect all recipe names
        Set<String> recipeNames = new HashSet<>();
        for (Map<String, String> dayMeals : mealsMap.values()) {
            for (String recipeName : dayMeals.values()) {
                if (recipeName != null && !recipeName.isBlank() &&
                    !recipeName.toLowerCase().contains("no suitable")) {
                    recipeNames.add(recipeName);
                }
            }
        }

        return generateShoppingListFromRecipeNames(recipeNames, null, null);
    }

    /**
     * Core method to generate shopping list from a MealPlan entity.
     */
    private ShoppingListDTO generateShoppingListFromMealPlan(MealPlan mealPlan) {
        // Parse the meals JSON to get recipe names
        Set<String> recipeNames = new HashSet<>();

        if (mealPlan.getMealsJson() != null && !mealPlan.getMealsJson().isEmpty()) {
            try {
                Map<String, Map<String, String>> mealsMap = objectMapper.readValue(
                    mealPlan.getMealsJson(),
                    new TypeReference<Map<String, Map<String, String>>>() {}
                );

                for (Map<String, String> dayMeals : mealsMap.values()) {
                    for (String recipeName : dayMeals.values()) {
                        if (recipeName != null && !recipeName.isBlank() &&
                            !recipeName.toLowerCase().contains("no suitable")) {
                            recipeNames.add(recipeName);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing meals JSON for meal plan {}: {}", mealPlan.getId(), e.getMessage());
            }
        }

        // Also check the direct recipe associations
        if (mealPlan.getMeals() != null) {
            for (Recipe recipe : mealPlan.getMeals()) {
                if (recipe.getName() != null) {
                    recipeNames.add(recipe.getName());
                }
            }
        }

        return generateShoppingListFromRecipeNames(recipeNames, mealPlan.getId(),
            mealPlan.getDate() != null ? mealPlan.getDate().toString() : null);
    }

    /**
     * Generates shopping list from a set of recipe names.
     */
    private ShoppingListDTO generateShoppingListFromRecipeNames(Set<String> recipeNames,
                                                                 Long mealPlanId,
                                                                 String mealPlanDate) {
        // Map to aggregate ingredients: ingredient name -> count
        Map<String, Integer> ingredientCounts = new HashMap<>();

        // Get user allergies for filtering (optional)
        Set<String> userAllergies = getUserAllergies();

        // Process each recipe
        for (String recipeName : recipeNames) {
            Optional<Recipe> recipeOpt = recipeRepository.findByName(recipeName);
            if (recipeOpt.isPresent()) {
                Recipe recipe = recipeOpt.get();

                // Get ingredients from the recipe
                if (recipe.getIngredients() != null) {
                    for (Food ingredient : recipe.getIngredients()) {
                        String ingredientName = ingredient.getName();

                        // Skip if ingredient matches user allergy
                        if (isAllergen(ingredientName, userAllergies)) {
                            log.debug("Skipping allergenic ingredient: {}", ingredientName);
                            continue;
                        }

                        // Aggregate the ingredient
                        ingredientCounts.merge(ingredientName, 1, Integer::sum);
                    }
                }
            } else {
                log.debug("Recipe '{}' not found in database", recipeName);
            }
        }

        // Categorize ingredients
        Map<String, List<ShoppingItem>> itemsByCategory = categorizeIngredients(ingredientCounts);

        // Calculate totals
        int totalItems = ingredientCounts.size();
        double estimatedCost = calculateEstimatedCost(itemsByCategory);

        ShoppingListDTO dto = new ShoppingListDTO();
        dto.setMealPlanId(mealPlanId);
        dto.setMealPlanDate(mealPlanDate);
        dto.setItemsByCategory(itemsByCategory);
        dto.setTotalItems(totalItems);
        dto.setEstimatedCost(estimatedCost);

        log.info("Generated shopping list with {} items across {} categories, estimated cost: {}",
                 totalItems, itemsByCategory.size(), estimatedCost);

        return dto;
    }

    /**
     * Categorizes ingredients into shopping categories.
     */
    private Map<String, List<ShoppingItem>> categorizeIngredients(Map<String, Integer> ingredientCounts) {
        Map<String, List<ShoppingItem>> categorized = new LinkedHashMap<>();

        // Initialize all categories
        categorized.put("produce", new ArrayList<>());
        categorized.put("meat", new ArrayList<>());
        categorized.put("dairy", new ArrayList<>());
        categorized.put("bakery", new ArrayList<>());
        categorized.put("pantry", new ArrayList<>());
        categorized.put("other", new ArrayList<>());

        for (Map.Entry<String, Integer> entry : ingredientCounts.entrySet()) {
            String ingredientName = entry.getKey();
            int quantity = entry.getValue();

            String category = determineCategory(ingredientName);
            double price = estimateItemPrice(ingredientName);

            ShoppingItem item = new ShoppingItem(ingredientName, quantity, "unit", price * quantity);
            categorized.get(category).add(item);
        }

        // Sort items within each category alphabetically
        for (List<ShoppingItem> items : categorized.values()) {
            items.sort(Comparator.comparing(ShoppingItem::getName, String.CASE_INSENSITIVE_ORDER));
        }

        // Remove empty categories
        categorized.entrySet().removeIf(e -> e.getValue().isEmpty());

        return categorized;
    }

    /**
     * Determines the category for an ingredient.
     */
    private String determineCategory(String ingredientName) {
        String lowerName = ingredientName.toLowerCase();

        for (Map.Entry<String, Pattern> entry : CATEGORY_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(lowerName).find()) {
                return entry.getKey();
            }
        }

        return "other";
    }

    /**
     * Estimates the price for a single unit of an ingredient (in PHP).
     */
    private double estimateItemPrice(String ingredientName) {
        String lowerName = ingredientName.toLowerCase();

        // Basic price estimation based on category/keywords
        if (lowerName.contains("chicken") || lowerName.contains("beef") || lowerName.contains("pork")) {
            return 150.0;
        } else if (lowerName.contains("fish") || lowerName.contains("salmon") || lowerName.contains("shrimp")) {
            return 200.0;
        } else if (lowerName.contains("cheese")) {
            return 120.0;
        } else if (lowerName.contains("milk") || lowerName.contains("yogurt")) {
            return 80.0;
        } else if (lowerName.contains("egg")) {
            return 60.0;
        } else if (lowerName.contains("bread") || lowerName.contains("rice") || lowerName.contains("pasta")) {
            return 50.0;
        } else if (lowerName.contains("oil") || lowerName.contains("butter")) {
            return 100.0;
        } else if (CATEGORY_PATTERNS.get("produce").matcher(lowerName).find()) {
            return 40.0;
        } else if (CATEGORY_PATTERNS.get("pantry").matcher(lowerName).find()) {
            return 60.0;
        }

        return 50.0; // Default price
    }

    /**
     * Calculates the total estimated cost.
     */
    private double calculateEstimatedCost(Map<String, List<ShoppingItem>> itemsByCategory) {
        double total = 0;
        for (List<ShoppingItem> items : itemsByCategory.values()) {
            for (ShoppingItem item : items) {
                total += item.getEstimatedPrice();
            }
        }
        return total;
    }

    /**
     * Gets the current user's allergies.
     */
    private Set<String> getUserAllergies() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                // Use the allergy service to get user allergies
                Set<String> allergies = allergyService.getUserAllergyNames(email);
                log.debug("Retrieved {} allergies for user {}", allergies.size(), email);
                return allergies;
            }
        } catch (Exception e) {
            log.warn("Could not retrieve user allergies: {}", e.getMessage());
        }
        return Collections.emptySet();
    }

    /**
     * Checks if an ingredient contains any allergens.
     */
    private boolean isAllergen(String ingredientName, Set<String> allergies) {
        if (allergies == null || allergies.isEmpty()) {
            return false;
        }

        String lowerIngredient = ingredientName.toLowerCase();
        for (String allergy : allergies) {
            if (lowerIngredient.contains(allergy.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates an empty shopping list DTO.
     */
    private ShoppingListDTO createEmptyShoppingList(Long mealPlanId, String reason) {
        ShoppingListDTO dto = new ShoppingListDTO();
        dto.setMealPlanId(mealPlanId);
        dto.setItemsByCategory(new LinkedHashMap<>());
        dto.setTotalItems(0);
        dto.setEstimatedCost(0);
        log.info("Created empty shopping list: {}", reason);
        return dto;
    }
}
