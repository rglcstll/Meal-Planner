package com.example.demo.service; // Ensure this package matches your project structure

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines the structure for each ingredient entry in ingredients.json.
 */
public class IngredientDefinition {
    private final String name;
    private final NutritionInfo nutrition;

    /**
     * Constructor for Jackson deserialization.
     * @param name Name of the ingredient.
     * @param nutrition Nutritional information for the ingredient.
     */
    @JsonCreator
    public IngredientDefinition(
            @JsonProperty("name") String name,
            @JsonProperty("nutrition") NutritionInfo nutrition) {
        this.name = name;
        this.nutrition = nutrition;
    }

    // Getters
    public String getName() {
        return name;
    }

    public NutritionInfo getNutrition() {
        return nutrition;
    }

    @Override
    public String toString() {
        return "IngredientDefinition{" +
               "name='" + name + '\'' +
               ", nutrition=" + nutrition +
               '}';
    }
}
