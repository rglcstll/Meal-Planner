package com.example.demo.model;

import jakarta.persistence.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;

@Entity
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Lob
    @Column(columnDefinition="TEXT")
    private String instructions;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_ingredient",
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "food_id")
    )
    private List<Food> ingredients = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_dietary_tags", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "tag")
    private List<String> dietaryTags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_allergens", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "allergen_name")
    private Set<String> allergens = new HashSet<>();

    @Column(name = "total_calories")
    private Integer totalCalories = 0;

    @Column(name = "total_protein")
    private Double totalProtein = 0.0;

    @Column(name = "total_carbs")
    private Double totalCarbs = 0.0;

    @Column(name = "total_fats")
    private Double totalFats = 0.0;
    
    @Column(name = "prep_time")
    private Integer prepTime;

    @Column(name = "cook_time")
    private Integer cookTime;

    @Column(name = "servings")
    private Integer servings;

    @Column(name = "difficulty")
    private String difficulty;

    @Lob // Good for potentially long text
    @Column(name = "benefit_description", columnDefinition="TEXT") // Added field
    private String benefitDescription;

    // Constructors
    public Recipe() {
    }

    // Constructor including all fields (including new benefitDescription)
    public Recipe(String name, String instructions, List<Food> ingredients, List<String> dietaryTags, Set<String> allergens,
                  Integer totalCalories, Double totalProtein, Double totalCarbs, Double totalFats,
                  Integer prepTime, Integer cookTime, Integer servings, String difficulty, String benefitDescription) {
        this.name = name;
        this.instructions = instructions;
        this.ingredients = ingredients != null ? new ArrayList<>(ingredients) : new ArrayList<>();
        this.dietaryTags = dietaryTags != null ? new ArrayList<>(dietaryTags) : new ArrayList<>();
        this.allergens = allergens != null ? new HashSet<>(allergens) : new HashSet<>();
        this.totalCalories = totalCalories != null ? totalCalories : 0;
        this.totalProtein = totalProtein != null ? totalProtein : 0.0;
        this.totalCarbs = totalCarbs != null ? totalCarbs : 0.0;
        this.totalFats = totalFats != null ? totalFats : 0.0;
        this.prepTime = prepTime;
        this.cookTime = cookTime;
        this.servings = servings;
        this.difficulty = difficulty;
        this.benefitDescription = benefitDescription; // Assign new field
    }
    
    // Simplified constructor (nutrition and benefit description will be set later or manually)
    public Recipe(String name, String instructions, List<String> dietaryTags, Set<String> allergens) {
        this.name = name;
        this.instructions = instructions;
        this.dietaryTags = dietaryTags != null ? new ArrayList<>(dietaryTags) : new ArrayList<>();
        this.allergens = allergens != null ? new HashSet<>(allergens) : new HashSet<>();
        this.ingredients = new ArrayList<>();
        // benefitDescription will be null by default
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

    public List<Food> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<Food> ingredients) {
        this.ingredients = ingredients != null ? new ArrayList<>(ingredients) : new ArrayList<>();
    }

    public List<String> getDietaryTags() {
        return dietaryTags;
    }

    public void setDietaryTags(List<String> dietaryTags) {
        this.dietaryTags = dietaryTags != null ? new ArrayList<>(dietaryTags) : new ArrayList<>();
    }

    public Set<String> getAllergens() {
        return allergens;
    }

    public void setAllergens(Set<String> allergens) {
        this.allergens = allergens != null ? new HashSet<>(allergens) : new HashSet<>();
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
        return "Recipe{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", instructions='" + (instructions != null ? instructions.substring(0, Math.min(instructions.length(), 50)) + "..." : "null") + '\'' +
               ", ingredients_count=" + (ingredients != null ? ingredients.size() : 0) +
               ", dietaryTags=" + dietaryTags +
               ", allergens=" + allergens +
               ", totalCalories=" + totalCalories +
               ", benefitDescription='" + (benefitDescription != null ? benefitDescription.substring(0, Math.min(benefitDescription.length(), 30)) + "..." : "null") + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recipe recipe = (Recipe) o;
        if (id != null && recipe.id != null) {
            return Objects.equals(id, recipe.id);
        }
        return Objects.equals(name, recipe.name);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(name);
    }
}
