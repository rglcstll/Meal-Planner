package com.example.demo.service; // Ensure this package matches your project structure

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Holds nutritional information for a meal or ingredient.
 * Used by MealDefinition and IngredientDefinition.
 * Ensure all fields in your JSON's "nutrition" object can be mapped here.
 */
public class NutritionInfo {
    private final int calories;
    private final double protein;
    private final double carbs;
    private final double fat;

    /**
     * Constructor for Jackson deserialization.
     * @param calories Calories for the item.
     * @param protein Protein in grams.
     * @param carbs Carbohydrates in grams.
     * @param fat Fat in grams.
     */
    @JsonCreator
    public NutritionInfo(
            @JsonProperty("calories") int calories,
            @JsonProperty("protein") double protein,
            @JsonProperty("carbs") double carbs,
            @JsonProperty("fat") double fat) {
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
    }

    // Getters
    public int getCalories() {
        return calories;
    }

    public double getProtein() {
        return protein;
    }

    public double getCarbs() {
        return carbs;
    }

    public double getFat() {
        return fat;
    }

    @Override
    public String toString() {
        return "NutritionInfo{" +
               "calories=" + calories +
               ", protein=" + protein +
               ", carbs=" + carbs +
               ", fat=" + fat +
               '}';
    }
}
