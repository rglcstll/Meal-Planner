package com.example.demo.repository;

import com.example.demo.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // ✅ Find a recipe by its name
    Optional<Recipe> findByName(String name);

    // ✅ Fetch all recipes with ingredients to prevent LazyInitializationException
    @Query("SELECT r FROM Recipe r LEFT JOIN FETCH r.ingredients") 
    List<Recipe> findAllWithIngredients();
    
    // ✅ Fetch a recipe by ID with its ingredients
    @Query("SELECT r FROM Recipe r LEFT JOIN FETCH r.ingredients WHERE r.id = :id")
    Optional<Recipe> findByIdWithIngredients(Long id);

    // ✅ Find recipes that have a specific dietary tag (Case Insensitive)
    @Query("SELECT DISTINCT r FROM Recipe r JOIN r.dietaryTags t WHERE LOWER(t) = LOWER(:tag)")
    List<Recipe> findByDietaryTag(String tag);
}
