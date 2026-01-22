package com.example.demo.service; // Ensure this package matches your project structure

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Collections;

/**
 * Defines the structure for each meal entry in meals.json.
 * Includes all details: name, nutrition, ingredients, instructions, tags, allergens, metadata, and benefit description.
 */
public class MealDefinition {
    private final String name;
    private final NutritionInfo nutrition;
    private final List<String> ingredientNames;
    private final String instructions;
    private final List<String> dietaryTags;
    private final List<String> allergens;
    private final Integer prepTime;
    private final Integer cookTime;
    private final Integer servings;
    private final String difficulty;
    private final String benefitDescription; // Added field

    /**
     * Constructor for Jackson deserialization.
     * All parameters correspond to fields in the meals.json objects.
     */
    @JsonCreator
    public MealDefinition(
            @JsonProperty("name") String name,
            @JsonProperty("nutrition") NutritionInfo nutrition,
            @JsonProperty("ingredientNames") List<String> ingredientNames,
            @JsonProperty("instructions") String instructions,
            @JsonProperty("dietaryTags") List<String> dietaryTags,
            @JsonProperty("allergens") List<String> allergens,
            @JsonProperty("prepTime") Integer prepTime,
            @JsonProperty("cookTime") Integer cookTime,
            @JsonProperty("servings") Integer servings,
            @JsonProperty("difficulty") String difficulty,
            @JsonProperty("benefitDescription") String benefitDescription) { // Added parameter
        this.name = name;
        this.nutrition = nutrition;
        this.ingredientNames = ingredientNames != null ? Collections.unmodifiableList(ingredientNames) : Collections.emptyList();
        this.instructions = instructions;
        this.dietaryTags = dietaryTags != null ? Collections.unmodifiableList(dietaryTags) : Collections.emptyList();
        this.allergens = allergens != null ? Collections.unmodifiableList(allergens) : Collections.emptyList();
        this.prepTime = prepTime;
        this.cookTime = cookTime;
        this.servings = servings;
        this.difficulty = difficulty;
        this.benefitDescription = benefitDescription; // Assign new field
    }

    // Getters
    public String getName() { return name; }
    public NutritionInfo getNutrition() { return nutrition; }
    public List<String> getIngredientNames() { return ingredientNames; }
    public String getInstructions() { return instructions; }
    public List<String> getDietaryTags() { return dietaryTags; }
    public List<String> getAllergens() { return allergens; }
    public Integer getPrepTime() { return prepTime; }
    public Integer getCookTime() { return cookTime; }
    public Integer getServings() { return servings; }
    public String getDifficulty() { return difficulty; }
    public String getBenefitDescription() { return benefitDescription; } // Added getter

    @Override
    public String toString() {
        return "MealDefinition{" +
               "name='" + name + '\'' +
               // Add other fields if needed for debugging, keeping it concise here
               '}';
    }
}
