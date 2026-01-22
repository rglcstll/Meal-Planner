package com.example.demo.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MealPlanDTO {
    private Long id; // Optional: if you want to return the ID of the saved plan from the backend
    private String date; // Expected format from frontend: "YYYY-MM-DD"
    private Map<String, Map<String, String>> meals; // Day (e.g., "MONDAY") -> MealType (e.g., "BREAKFAST") -> Recipe Name
    private Long userId; // Optional: if plans are user-specific, to link back to a User entity

    // Default constructor (needed by Jackson for deserialization)
    public MealPlanDTO() {
    }

    // Constructor with all fields (optional, for convenience)
    public MealPlanDTO(Long id, String date, Map<String, Map<String, String>> meals, Long userId) {
        this.id = id;
        this.date = date;
        this.meals = meals;
        this.userId = userId;
    }

	// Getters and Setters for all fields
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Map<String, Map<String, String>> getMeals() {
        return meals;
    }

    public void setMeals(Map<String, Map<String, String>> meals) {
        this.meals = meals;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    // equals, hashCode, and toString methods (good practice)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MealPlanDTO that = (MealPlanDTO) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(date, that.date) &&
               Objects.equals(meals, that.meals) &&
               Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, date, meals, userId);
    }

    @Override
    public String toString() {
        return "MealPlanDTO{" +
               "id=" + id +
               ", date='" + date + '\'' +
               ", meals=" + (meals != null ? meals.size() + " entries" : "null") + // Avoid printing large map
               ", userId=" + userId +
               '}';
    }
}
