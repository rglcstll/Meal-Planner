package com.example.demo.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class RecipeDTO {
    private Long id;
    private String name;
    private String instructions;
    private List<FoodDTO> ingredients = new ArrayList<>();
    private List<String> dietaryTags = new ArrayList<>();
    private Set<String> allergens = new HashSet<>();

    private Integer totalCalories;
    private Double totalProtein;
    private Double totalCarbs;
    private Double totalFats;

    private Integer prepTime;
    private Integer cookTime;
    private Integer servings;
    private String difficulty;
    private String benefitDescription; // Added field

    // Default constructor
    public RecipeDTO() {
    }

    // Constructor for convenience (including new benefitDescription)
    public RecipeDTO(Long id, String name, String instructions, List<FoodDTO> ingredients, List<String> dietaryTags, Set<String> allergens,
                     Integer totalCalories, Double totalProtein, Double totalCarbs, Double totalFats,
                     Integer prepTime, Integer cookTime, Integer servings, String difficulty, String benefitDescription) {
        this.id = id;
        this.name = name;
        this.instructions = instructions;
        this.ingredients = ingredients != null ? ingredients : new ArrayList<>();
        this.dietaryTags = dietaryTags != null ? dietaryTags : new ArrayList<>();
        this.allergens = allergens != null ? allergens : new HashSet<>();
        this.totalCalories = totalCalories;
        this.totalProtein = totalProtein;
        this.totalCarbs = totalCarbs;
        this.totalFats = totalFats;
        this.prepTime = prepTime;
        this.cookTime = cookTime;
        this.servings = servings;
        this.difficulty = difficulty;
        this.benefitDescription = benefitDescription; // Assign new field
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public List<FoodDTO> getIngredients() {
        return ingredients == null ? new ArrayList<>() : ingredients;
    }

    public void setIngredients(List<FoodDTO> ingredients) {
        this.ingredients = ingredients;
    }

    public List<String> getDietaryTags() {
        return dietaryTags == null ? new ArrayList<>() : dietaryTags;
    }

    public void setDietaryTags(List<String> dietaryTags) {
        this.dietaryTags = dietaryTags;
    }

    public Set<String> getAllergens() {
        return allergens == null ? new HashSet<>() : allergens;
    }

    public void setAllergens(Set<String> allergens) {
        this.allergens = allergens;
    }

    public Integer getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(Integer totalCalories) {
        this.totalCalories = totalCalories;
    }

    public Double getTotalProtein() {
        return totalProtein;
    }

    public void setTotalProtein(Double totalProtein) {
        this.totalProtein = totalProtein;
    }

    public Double getTotalCarbs() {
        return totalCarbs;
    }

    public void setTotalCarbs(Double totalCarbs) {
        this.totalCarbs = totalCarbs;
    }

    public Double getTotalFats() {
        return totalFats;
    }

    public void setTotalFats(Double totalFats) {
        this.totalFats = totalFats;
    }
    
    public Integer getPrepTime() {
        return prepTime;
    }

    public void setPrepTime(Integer prepTime) {
        this.prepTime = prepTime;
    }

    public Integer getCookTime() {
        return cookTime;
    }

    public void setCookTime(Integer cookTime) {
        this.cookTime = cookTime;
    }

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getBenefitDescription() { // Added getter
        return benefitDescription;
    }

    public void setBenefitDescription(String benefitDescription) { // Added setter
        this.benefitDescription = benefitDescription;
    }

    @Override
    public String toString() {
        return "RecipeDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", totalCalories=" + totalCalories +
                ", benefitDescription='" + (benefitDescription != null ? benefitDescription.substring(0, Math.min(benefitDescription.length(), 30)) + "..." : "null") + '\'' +
                '}';
    }
}
