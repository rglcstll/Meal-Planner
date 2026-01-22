package com.example.demo.repository;

import com.example.demo.model.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {

    // ✅ Return Optional<Food> to handle missing foods gracefully
    Optional<Food> findByName(String name);

    // ✅ Add method to find foods by dietary tag (using MEMBER OF)
    @Query("SELECT f FROM Food f WHERE :dietaryTagName MEMBER OF f.dietaryTags")
    List<Food> findFoodsByDietaryTag(String dietaryTagName);

    // ✅ Add method to fetch all foods
    List<Food> findAll();
}
