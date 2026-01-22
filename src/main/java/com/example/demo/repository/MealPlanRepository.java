package com.example.demo.repository;

import com.example.demo.model.MealPlan;
import com.example.demo.model.User; // Import if you plan to use findByUserAndDate
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional; // Required for Optional return type

@Repository // Good practice to add @Repository annotation
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    // Method to find meal plans by user's email (if you keep user-specific plans this way)
    List<MealPlan> findByUserEmail(String userEmail);

    /**
     * Finds a meal plan by its specific date.
     * Since a user should ideally have only one plan per date (or one plan globally per date),
     * this method returns an Optional<MealPlan>.
     *
     * @param date The date of the meal plan.
     * @return An Optional containing the MealPlan if found, or an empty Optional.
     */
    Optional<MealPlan> findByDate(LocalDate date); // <<< CORRECTED: Changed from List<MealPlan>

    /**
     * Optional: If your meal plans are user-specific, a method like this would be more common.
     * Finds a meal plan for a specific user and date.
     *
     * @param user The user associated with the meal plan.
     * @param date The date of the meal plan.
     * @return An Optional containing the MealPlan if found, or an empty Optional.
     */
    // Optional<MealPlan> findByUserAndDate(User user, LocalDate date);
}