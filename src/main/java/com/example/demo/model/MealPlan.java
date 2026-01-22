package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "meal_plans")
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // Assuming user_id can be null if plans are not strictly user-specific initially
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    /**
     * Stores the structured meal plan (Day -> MealType -> RecipeName) as a JSON string.
     * Using columnDefinition = "TEXT" to ensure sufficient length in most databases.
     * Use "LONGTEXT" for MySQL if TEXT is still insufficient.
     */
    // @Lob // @Lob is often sufficient, but columnDefinition is more explicit.
    @Column(name = "meals_json", columnDefinition = "TEXT") // <<< MODIFIED: Explicitly use TEXT type
    private String mealsJson;

    /**
     * Represents the list of unique Recipe entities included in this meal plan.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "mealplan_recipe",
        joinColumns = @JoinColumn(name = "mealplan_id"),
        inverseJoinColumns = @JoinColumn(name = "recipe_id")
    )
    private List<Recipe> meals = new ArrayList<>();

    // Default Constructor (Required by JPA)
    public MealPlan() {}

    // Constructor with Parameters
    public MealPlan(User user, LocalDate date, List<Recipe> meals, String mealsJson) {
        this.user = user;
        this.date = date;
        this.meals = meals;
        this.mealsJson = mealsJson;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<Recipe> getMeals() {
        return meals;
    }

    public void setMeals(List<Recipe> meals) {
        this.meals = meals;
    }

    public String getMealsJson() {
        return mealsJson;
    }

    public void setMealsJson(String mealsJson) {
        this.mealsJson = mealsJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MealPlan mealPlan = (MealPlan) o;
        return Objects.equals(id, mealPlan.id) && Objects.equals(date, mealPlan.date) && Objects.equals(user, mealPlan.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, date, user);
    }

    @Override
    public String toString() {
        return "MealPlan{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : "null") +
                ", date=" + date +
                ", mealsJson='" + (mealsJson != null && mealsJson.length() > 50 ? mealsJson.substring(0, 50) + "..." : mealsJson) + '\'' +
                ", mealsCount=" + (meals != null ? meals.size() : 0) +
                '}';
    }
}
