package com.example.demo.controller;

import com.example.demo.dto.RecipeDTO;
// import com.example.demo.model.Recipe; // No longer needed for request/response body
// import com.example.demo.repository.RecipeRepository; // Repository interaction is in the service
import com.example.demo.service.RecipeService;

import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus; // Import HttpStatus
import org.springframework.http.ResponseEntity; // Import ResponseEntity
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.EntityNotFoundException; // Import specific exception

import java.util.List;
// import java.util.Optional; // No longer needed here

@RestController
@RequestMapping("/api/recipes") // Base path for recipe-related operations
public class RecipeController {

    private static final Logger log = LoggerFactory.getLogger(RecipeController.class);

    private final RecipeService recipeService; // Inject RecipeService via constructor

    // Constructor injection is preferred
    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /**
     * GET /api/recipes : Get all recipes (including ingredients).
     * @return List of RecipeDTOs.
     */
    @GetMapping
    public ResponseEntity<List<RecipeDTO>> getAllRecipes() {
        log.info("Received request GET /api/recipes");
        List<RecipeDTO> recipes = recipeService.getAllRecipes(); // Use service method returning DTOs
        return ResponseEntity.ok(recipes);
    }

    /**
     * GET /api/recipes/{id} : Get a specific recipe by ID (including ingredients).
     * @param id The ID of the recipe.
     * @return ResponseEntity containing RecipeDTO or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecipeDTO> getRecipeById(@PathVariable Long id) {
        log.info("Received request GET /api/recipes/{}", id);
        try {
            RecipeDTO recipe = recipeService.getRecipeById(id);
            return ResponseEntity.ok(recipe);
        } catch (EntityNotFoundException e) {
            log.warn("Recipe not found for ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
             log.error("Error fetching recipe ID {}: {}", id, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST /api/recipes : Add a new recipe.
     * Accepts a RecipeDTO in the request body.
     * @param recipeDTO The DTO containing new recipe details.
     * @return ResponseEntity containing the created RecipeDTO or 400/500 error.
     */
    @PostMapping
    public ResponseEntity<RecipeDTO> createRecipe(@RequestBody RecipeDTO recipeDTO) {
        log.info("Received request POST /api/recipes with name: {}", recipeDTO.getName());
        try {
            // Basic validation example (add more as needed)
            if (recipeDTO.getName() == null || recipeDTO.getName().isBlank()) {
                 log.warn("Create recipe request failed: Name is required.");
                 return ResponseEntity.badRequest().body(null); // Or return an error DTO
            }
            RecipeDTO createdRecipe = recipeService.createRecipe(recipeDTO);
            // Return 201 Created status with the created DTO
            return ResponseEntity.status(HttpStatus.CREATED).body(createdRecipe);
        } catch (IllegalArgumentException e) {
            log.warn("Create recipe request failed due to invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null); // Return 400 Bad Request
        } catch (Exception e) {
            log.error("Error creating recipe '{}': {}", recipeDTO.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Or return an error DTO
        }
    }

    /**
     * PUT /api/recipes/{id} : Update an existing recipe.
     * Accepts a RecipeDTO in the request body.
     * @param id The ID of the recipe to update.
     * @param recipeDTO The DTO containing updated details.
     * @return ResponseEntity containing the updated RecipeDTO or 404/400/500 error.
     */
    @PutMapping("/{id}")
    public ResponseEntity<RecipeDTO> updateRecipe(@PathVariable Long id, @RequestBody RecipeDTO recipeDTO) {
        log.info("Received request PUT /api/recipes/{} with name: {}", id, recipeDTO.getName());
        try {
             // Basic validation example
            if (recipeDTO.getName() == null || recipeDTO.getName().isBlank()) {
                 log.warn("Update recipe request failed for ID {}: Name is required.", id);
                 return ResponseEntity.badRequest().body(null);
            }
            RecipeDTO updatedRecipe = recipeService.updateRecipe(id, recipeDTO);
            return ResponseEntity.ok(updatedRecipe);
        } catch (EntityNotFoundException e) {
            log.warn("Recipe not found for update, ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Update recipe request failed for ID {} due to invalid argument: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(null); // Return 400 Bad Request
        } catch (Exception e) {
             log.error("Error updating recipe ID {}: {}", id, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * DELETE /api/recipes/{id} : Delete a recipe.
     * @param id The ID of the recipe to delete.
     * @return ResponseEntity with 204 No Content on success, or 404/500 error.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long id) {
        log.info("Received request DELETE /api/recipes/{}", id);
        try {
            recipeService.deleteRecipe(id);
            log.info("Successfully deleted recipe ID: {}", id);
            return ResponseEntity.noContent().build(); // HTTP 204 No Content
        } catch (EntityNotFoundException e) {
             log.warn("Recipe not found for deletion, ID: {}", id);
             return ResponseEntity.notFound().build(); // HTTP 404 Not Found
        } catch (Exception e) {
             // Catch potential database errors (e.g., constraint violations if recipe is in use)
             log.error("Error deleting recipe ID {}: {}", id, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // HTTP 500
        }
    }
}
