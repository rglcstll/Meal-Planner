package com.example.demo.service;

import com.example.demo.dto.FoodDTO;
import com.example.demo.dto.RecipeDTO;
import com.example.demo.model.Food;
import com.example.demo.model.Recipe;
import com.example.demo.repository.FoodRepository;
import com.example.demo.repository.RecipeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final RecipeRepository recipeRepository;
    private final FoodRepository foodRepository;

    public RecipeService(RecipeRepository recipeRepository, FoodRepository foodRepository) {
        this.recipeRepository = recipeRepository;
        this.foodRepository = foodRepository;
    }

    private FoodDTO convertFoodToDTO(Food food) {
        if (food == null) return null;
        FoodDTO dto = new FoodDTO();
        dto.setId(food.getId());
        dto.setName(food.getName());
        dto.setCalories(food.getCalories());
        dto.setProtein(food.getProtein());
        dto.setCarbs(food.getCarbs());
        dto.setFats(food.getFats());
        return dto;
    }

    public RecipeDTO convertToDTO(Recipe recipe) {
        if (recipe == null) return null;

        // Ensure ingredients are loaded before attempting to access them
        List<FoodDTO> ingredientDTOs = recipe.getIngredients() == null ? new ArrayList<>() :
                recipe.getIngredients().stream()
                      .map(this::convertFoodToDTO)
                      .collect(Collectors.toList());

        List<String> dietaryTags = recipe.getDietaryTags() == null ? new ArrayList<>() :
                new ArrayList<>(recipe.getDietaryTags());
        
        Set<String> allergens = recipe.getAllergens() == null ? new HashSet<>() :
                new HashSet<>(recipe.getAllergens());

        return new RecipeDTO(
                recipe.getId(),
                recipe.getName(),
                recipe.getInstructions(),
                ingredientDTOs,
                dietaryTags,
                allergens,
                recipe.getTotalCalories(),
                recipe.getTotalProtein(),
                recipe.getTotalCarbs(),
                recipe.getTotalFats(),
                recipe.getPrepTime(),
                recipe.getCookTime(),
                recipe.getServings(),
                recipe.getDifficulty(),
                recipe.getBenefitDescription()
        );
    }

    private Recipe convertToEntity(RecipeDTO recipeDTO, Recipe existingRecipe) {
        Recipe recipe = (existingRecipe != null) ? existingRecipe : new Recipe();

        recipe.setName(recipeDTO.getName());
        recipe.setInstructions(recipeDTO.getInstructions());
        recipe.setDietaryTags(recipeDTO.getDietaryTags() != null ? new ArrayList<>(recipeDTO.getDietaryTags()) : new ArrayList<>());
        recipe.setAllergens(recipeDTO.getAllergens() != null ? new HashSet<>(recipeDTO.getAllergens()) : new HashSet<>());
        
        recipe.setPrepTime(recipeDTO.getPrepTime());
        recipe.setCookTime(recipeDTO.getCookTime());
        recipe.setServings(recipeDTO.getServings());
        recipe.setDifficulty(recipeDTO.getDifficulty());
        recipe.setBenefitDescription(recipeDTO.getBenefitDescription());

        List<Food> ingredientEntities = new ArrayList<>();
        if (recipeDTO.getIngredients() != null) {
            for (FoodDTO foodDTO : recipeDTO.getIngredients()) {
                Food foodEntity = null;
                if (foodDTO.getId() != null) {
                    foodEntity = foodRepository.findById(foodDTO.getId()).orElse(null);
                }
                if (foodEntity == null && foodDTO.getName() != null && !foodDTO.getName().isBlank()) {
                    foodEntity = foodRepository.findByName(foodDTO.getName()).orElse(null);
                     if (foodEntity == null) {
                        log.warn("Ingredient '{}' specified in DTO not found in database by name. Cannot add to recipe.", foodDTO.getName());
                    }
                }

                if (foodEntity != null) {
                    ingredientEntities.add(foodEntity);
                } else if (foodDTO.getId() != null || (foodDTO.getName() != null && !foodDTO.getName().isBlank())) {
                    log.warn("Food item '{}' (ID: {}) specified in RecipeDTO not found in database. Skipping ingredient for recipe '{}'.", foodDTO.getName(), foodDTO.getId(), recipeDTO.getName());
                }
            }
        }
        recipe.setIngredients(ingredientEntities);

        // Nutrition calculation should ideally happen here or be triggered
        recipe.setTotalCalories(recipeDTO.getTotalCalories());
        recipe.setTotalProtein(recipeDTO.getTotalProtein());
        recipe.setTotalCarbs(recipeDTO.getTotalCarbs());
        recipe.setTotalFats(recipeDTO.getTotalFats());

        return recipe;
    }

    @Transactional(readOnly = true)
    public List<RecipeDTO> getAllRecipes() {
        log.info("Fetching all recipes and converting to DTOs");
        // Use findAllWithIngredients to ensure ingredients are fetched for DTO conversion
        return recipeRepository.findAllWithIngredients().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Recipe> getAllAvailableRecipes() {
        log.info("Fetching all available Recipe entities with their ingredients (for meal generation)");
        // MODIFICATION: Use findAllWithIngredients to ensure ingredients are loaded
        return recipeRepository.findAllWithIngredients();
    }

    @Transactional(readOnly = true)
    public RecipeDTO getRecipeById(Long id) {
        log.info("Fetching recipe by ID: {} with ingredients and converting to DTO", id);
        // MODIFICATION: Use findByIdWithIngredients
        Recipe recipe = recipeRepository.findByIdWithIngredients(id)
                .orElseThrow(() -> {
                    log.warn("Recipe not found with ID: {}", id);
                    return new EntityNotFoundException("Recipe not found with id: " + id);
                });
        return convertToDTO(recipe);
    }

    @Transactional
    public RecipeDTO createRecipe(RecipeDTO recipeDTO) {
        log.info("Attempting to create new recipe: {}", recipeDTO.getName());
        if (recipeDTO.getId() != null) {
             log.warn("RecipeDTO for creation has an ID ({}). This ID will be ignored as database generates IDs.", recipeDTO.getId());
             recipeDTO.setId(null);
        }
        if (recipeRepository.findByName(recipeDTO.getName()).isPresent()) {
            log.warn("Recipe with name '{}' already exists. Creation aborted.", recipeDTO.getName());
            throw new IllegalArgumentException("Recipe with name '" + recipeDTO.getName() + "' already exists.");
        }

        Recipe recipeEntity = convertToEntity(recipeDTO, null);
        Recipe savedRecipe = recipeRepository.save(recipeEntity);
        log.info("Successfully created recipe '{}' with ID: {}", savedRecipe.getName(), savedRecipe.getId());
        return convertToDTO(savedRecipe);
    }

    @Transactional
    public RecipeDTO updateRecipe(Long id, RecipeDTO recipeDTO) {
        log.info("Attempting to update recipe with ID: {}", id);
        // Fetch existing with ingredients to ensure all data is available for conversion and comparison
        Recipe existingRecipe = recipeRepository.findByIdWithIngredients(id)
                .orElseThrow(() -> {
                    log.warn("Recipe not found for update with ID: {}", id);
                    return new EntityNotFoundException("Recipe not found with id: " + id);
                });

        if (recipeDTO.getName() != null && !recipeDTO.getName().equalsIgnoreCase(existingRecipe.getName())) {
            recipeRepository.findByName(recipeDTO.getName()).ifPresent(collidingRecipe -> {
                if (!collidingRecipe.getId().equals(id)) {
                    log.warn("Cannot update recipe ID {}: new name '{}' conflicts with existing recipe ID {}", id, recipeDTO.getName(), collidingRecipe.getId());
                    throw new IllegalArgumentException("Recipe name '" + recipeDTO.getName() + "' already exists.");
                }
            });
        }

        Recipe updatedRecipeEntity = convertToEntity(recipeDTO, existingRecipe);
        Recipe savedRecipe = recipeRepository.save(updatedRecipeEntity);
        log.info("Successfully updated recipe '{}' with ID: {}", savedRecipe.getName(), savedRecipe.getId());
        return convertToDTO(savedRecipe);
    }

    @Transactional
    public void deleteRecipe(Long id) {
        log.info("Attempting to delete recipe with ID: {}", id);
        if (!recipeRepository.existsById(id)) {
            log.warn("Recipe not found for deletion with ID: {}", id);
            throw new EntityNotFoundException("Recipe not found with id: " + id + " for deletion.");
        }
        recipeRepository.deleteById(id);
        log.info("Successfully deleted recipe with ID: {}", id);
    }
}
