package com.example.demo.service;

import com.example.demo.model.MealType;
import com.example.demo.model.Recipe;
import com.example.demo.model.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating weekly meal plans from available recipes.
 * Uses deterministic scoring to avoid runtime randomness and unstable outputs.
 */
@Service
public class MealGenerationService {

    private static final Logger log = LoggerFactory.getLogger(MealGenerationService.class);

    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final int DEFAULT_DAILY_CALORIES = 2000;
    private static final int MIN_DAILY_CALORIES = 1200;

    private static final int SLOT_TAG_MATCH_SCORE = 100;
    private static final int SLOT_KEYWORD_MATCH_SCORE = 70;
    private static final int SLOT_FALLBACK_MATCH_SCORE = 35;
    private static final int DIET_SPECIFICITY_SCORE = 20;
    private static final int STALE_NAME_PENALTY = 120;
    private static final int REPEAT_PENALTY_STEP = 25;

    private static final Set<String> STALE_NAME_TOKENS = Set.of(
            "server error", "no suitable", "no recipe", "error generating"
    );

    private enum SlotSource {
        TAG, KEYWORD, FALLBACK
    }

    private static final class SlotCandidates {
        private final MealType slot;
        private final SlotSource source;
        private final List<Recipe> recipes;

        private SlotCandidates(MealType slot, SlotSource source, List<Recipe> recipes) {
            this.slot = slot;
            this.source = source;
            this.recipes = recipes;
        }
    }

    /**
     * Backward-compatible entry point.
     * Uses deterministic generation with a default calorie target.
     *
     * @param recipes List of recipes to use for plan generation
     * @return Map structure: Day -> MealType -> RecipeName
     */
    public Map<String, Map<String, String>> generateWeeklyPlan(List<Recipe> recipes) {
        return generateWeeklyPlanDeterministic(recipes, "standard", null);
    }

    /**
     * Deterministic weekly generation with scoring and tie-break rules.
     * Output is always 7-day shape with BREAKFAST/LUNCH/DINNER keys.
     */
    public Map<String, Map<String, String>> generateWeeklyPlanDeterministic(
            List<Recipe> safeRecipes,
            String dietType,
            User currentUser) {
        int dailyCalories = resolveDailyCalories(currentUser);
        String normalizedDiet = normalizeDiet(dietType);
        List<Recipe> recipes = sanitizeRecipes(safeRecipes);

        log.info("Meal generation start: dietType={}, dailyCalories={}, inputRecipes={}",
                normalizedDiet, dailyCalories, recipes.size());
        Map<String, Map<String, String>> weeklyPlan = new LinkedHashMap<>();
        for (String day : DAYS) {
            weeklyPlan.put(day, new LinkedHashMap<>());
            for (MealType slot : MealType.values()) {
                weeklyPlan.get(day).put(slot.name(), noSuitableMessage(slot));
            }
        }

        if (recipes.isEmpty()) {
            log.warn("No safe recipes available. Returning explicit no-suitable plan.");
            return weeklyPlan;
        }

        EnumMap<MealType, SlotCandidates> pools = new EnumMap<>(MealType.class);
        for (MealType slot : MealType.values()) {
            SlotCandidates candidates = buildSlotCandidates(slot, recipes);
            pools.put(slot, candidates);
            log.info("Slot candidate source: slot={}, source={}, count={}",
                    slot.name(), candidates.source, candidates.recipes.size());
        }

        Map<String, Integer> repeatCounts = new HashMap<>();

        for (String day : DAYS) {
            Map<String, String> dayMeals = weeklyPlan.get(day);
            for (MealType slot : MealType.values()) {
                SlotCandidates pool = pools.get(slot);
                if (pool == null || pool.recipes.isEmpty()) {
                    dayMeals.put(slot.name(), noSuitableMessage(slot));
                    continue;
                }

                int slotTarget = getSlotCalorieTarget(dailyCalories, slot);
                Recipe selected = chooseBestCandidate(pool, normalizedDiet, slotTarget, repeatCounts);
                if (selected == null || !hasText(selected.getName())) {
                    dayMeals.put(slot.name(), noSuitableMessage(slot));
                    continue;
                }

                dayMeals.put(slot.name(), selected.getName());
                repeatCounts.merge(recipeKey(selected), 1, Integer::sum);
            }
        }

        log.info("Successfully generated deterministic weekly meal plan for dietType={}", normalizedDiet);
        return weeklyPlan;
    }

    private List<Recipe> sanitizeRecipes(List<Recipe> rawRecipes) {
        if (rawRecipes == null || rawRecipes.isEmpty()) {
            return Collections.emptyList();
        }
        return rawRecipes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SlotCandidates buildSlotCandidates(MealType slot, List<Recipe> safeRecipes) {
        List<Recipe> tagCandidates = safeRecipes.stream()
                .filter(recipe -> hasExplicitSlotTag(recipe, slot))
                .collect(Collectors.toList());
        if (!tagCandidates.isEmpty()) {
            return new SlotCandidates(slot, SlotSource.TAG, tagCandidates);
        }

        List<Recipe> keywordCandidates = safeRecipes.stream()
                .filter(recipe -> matchesSlotKeyword(recipe, slot))
                .collect(Collectors.toList());
        if (!keywordCandidates.isEmpty()) {
            return new SlotCandidates(slot, SlotSource.KEYWORD, keywordCandidates);
        }

        return new SlotCandidates(slot, SlotSource.FALLBACK, safeRecipes);
    }

    private Recipe chooseBestCandidate(
            SlotCandidates pool,
            String normalizedDiet,
            int slotTargetCalories,
            Map<String, Integer> repeatCounts) {

        return pool.recipes.stream()
                .max(Comparator
                        .comparingDouble((Recipe recipe) ->
                                scoreRecipe(recipe, pool, normalizedDiet, slotTargetCalories, repeatCounts))
                        .thenComparing(recipe -> recipe.getId() == null ? Long.MAX_VALUE : recipe.getId(), Comparator.reverseOrder())
                        .thenComparing(recipe -> safeLower(recipe.getName()), Comparator.reverseOrder()))
                .orElse(null);
    }

    private double scoreRecipe(
            Recipe recipe,
            SlotCandidates pool,
            String normalizedDiet,
            int targetCalories,
            Map<String, Integer> repeatCounts) {

        int matchBaseScore;
        if (hasExplicitSlotTag(recipe, pool.slot)) {
            matchBaseScore = SLOT_TAG_MATCH_SCORE;
        } else if (matchesSlotKeyword(recipe, pool.slot)) {
            matchBaseScore = SLOT_KEYWORD_MATCH_SCORE;
        } else {
            matchBaseScore = SLOT_FALLBACK_MATCH_SCORE;
        }

        double calorieScore = calorieFitScore(recipe.getTotalCalories(), targetCalories);
        int dietScore = hasDietTag(recipe, normalizedDiet) ? DIET_SPECIFICITY_SCORE : 0;
        int repeatPenalty = repeatCounts.getOrDefault(recipeKey(recipe), 0) * REPEAT_PENALTY_STEP;
        int stalePenalty = isStaleRecipeName(recipe.getName()) ? STALE_NAME_PENALTY : 0;

        return matchBaseScore + calorieScore + dietScore - repeatPenalty - stalePenalty;
    }

    private double calorieFitScore(Integer recipeCalories, int targetCalories) {
        if (recipeCalories == null || recipeCalories <= 0 || targetCalories <= 0) {
            return 0;
        }
        double diffRatio = Math.abs(recipeCalories - targetCalories) / (double) targetCalories;
        double score = 50.0 - (diffRatio * 50.0);
        return Math.max(0.0, score);
    }

    private int resolveDailyCalories(User user) {
        if (user == null || user.getWeight() == null || user.getHeight() == null || user.getAge() == null) {
            return DEFAULT_DAILY_CALORIES;
        }

        double weight = user.getWeight();
        double height = user.getHeight();
        int age = user.getAge();
        String gender = safeLower(user.getGender());

        double bmr = ("female".equals(gender) || "f".equals(gender))
                ? (10 * weight) + (6.25 * height) - (5 * age) - 161
                : (10 * weight) + (6.25 * height) - (5 * age) + 5;

        double activityMultiplier = resolveActivityMultiplier(user.getActivityLevel());
        int target = (int) Math.round(bmr * activityMultiplier);
        return Math.max(target, MIN_DAILY_CALORIES);
    }

    private double resolveActivityMultiplier(String levelRaw) {
        String level = safeLower(levelRaw);
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
                return 1.725;
            case "very active":
            case "very_active":
            case "extra active":
            case "extra_active":
                return 1.9;
            default:
                return 1.55;
        }
    }

    private int getSlotCalorieTarget(int dailyCalories, MealType slot) {
        switch (slot) {
            case BREAKFAST:
                return (int) Math.round(dailyCalories * 0.25);
            case LUNCH:
                return (int) Math.round(dailyCalories * 0.35);
            case DINNER:
            default:
                return (int) Math.round(dailyCalories * 0.40);
        }
    }

    private boolean hasExplicitSlotTag(Recipe recipe, MealType slot) {
        if (recipe == null || recipe.getDietaryTags() == null) {
            return false;
        }
        return recipe.getDietaryTags().stream()
                .filter(Objects::nonNull)
                .map(this::safeLower)
                .anyMatch(tag -> tag.equals(slot.name().toLowerCase()) || isSlotAlias(tag, slot));
    }

    private boolean isSlotAlias(String tag, MealType slot) {
        if (slot == MealType.DINNER) {
            return "supper".equals(tag);
        }
        return false;
    }

    private boolean matchesSlotKeyword(Recipe recipe, MealType slot) {
        String name = safeLower(recipe != null ? recipe.getName() : null);
        if (!hasText(name)) {
            return false;
        }
        switch (slot) {
            case BREAKFAST:
                return containsAny(name, "egg", "oatmeal", "breakfast", "pancake", "yogurt", "smoothie", "toast", "cereal", "granola", "muffin");
            case LUNCH:
                return containsAny(name, "salad", "sandwich", "wrap", "soup", "quinoa", "hummus", "bowl", "platter", "leftover");
            case DINNER:
                return !containsAny(name, "breakfast", "cereal", "toast", "sandwich", "wrap", "smoothie", "yogurt", "oatmeal", "pancake", "parfait", "granola");
            default:
                return false;
        }
    }

    private boolean hasDietTag(Recipe recipe, String normalizedDiet) {
        if (!hasText(normalizedDiet) || "standard".equals(normalizedDiet) || "balanced".equals(normalizedDiet)) {
            return false;
        }
        if (recipe == null || recipe.getDietaryTags() == null) {
            return false;
        }
        return recipe.getDietaryTags().stream()
                .filter(Objects::nonNull)
                .map(this::safeLower)
                .anyMatch(normalizedDiet::equals);
    }

    private String normalizeDiet(String dietType) {
        String normalized = safeLower(dietType);
        return hasText(normalized) ? normalized : "standard";
    }

    private boolean isStaleRecipeName(String recipeName) {
        String normalized = safeLower(recipeName);
        if (!hasText(normalized)) {
            return true;
        }
        return STALE_NAME_TOKENS.stream().anyMatch(normalized::contains);
    }

    private String recipeKey(Recipe recipe) {
        if (recipe == null) {
            return "null";
        }
        if (recipe.getId() != null) {
            return "id:" + recipe.getId();
        }
        return "name:" + safeLower(recipe.getName());
    }

    private String noSuitableMessage(MealType slot) {
        switch (slot) {
            case BREAKFAST:
                return "No suitable breakfast found";
            case LUNCH:
                return "No suitable lunch found";
            case DINNER:
            default:
                return "No suitable dinner found";
        }
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeLower(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }
}
