package com.example.demo.controller;

import com.example.demo.dto.UserMealCompletionDTO;
import com.example.demo.model.User; // Your User model
// import com.example.demo.model.UserMealCompletion; // Not directly returned anymore
import com.example.demo.service.UserMealCompletionService;
import com.example.demo.service.UserService; // To fetch User by principal

import jakarta.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Spring Security's UserDetails
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/mealstatus") // Matches mealStatusApiUrl in dashboard.js
public class UserMealCompletionController {

    private static final Logger logger = LoggerFactory.getLogger(UserMealCompletionController.class);

    private final UserMealCompletionService userMealCompletionService;
    private final UserService userService; // To resolve UserDetails to your User entity

    @Autowired
    public UserMealCompletionController(UserMealCompletionService userMealCompletionService, UserService userService) {
        this.userMealCompletionService = userMealCompletionService;
        this.userService = userService;
    }

    /**
     * Gets all meal completion statuses for a user on a specific date.
     *
     * @param userId The ID of the user (from path variable).
     * @param date   The date (YYYY-MM-DD format from path variable).
     * @return A Map of meal slot keys to their UserMealCompletionDTO (containing done status and mealName).
     */
    @GetMapping("/{userId}/{date}")
    public ResponseEntity<Map<String, UserMealCompletionDTO>> getMealStatuses(
            @PathVariable Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserDetails principal) {

        if (principal == null) {
            logger.warn("Attempt to get meal statuses without authentication.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        com.example.demo.model.User authenticatedUser = userService.findByEmail(principal.getUsername());
        if (authenticatedUser == null || !authenticatedUser.getId().equals(userId)) {
            logger.warn("User {} attempting to access meal statuses for userId {}. Forbidden.",
                        principal.getUsername(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        logger.info("Request to get meal statuses for userId: {} on date: {}", userId, date);
        try {
            Map<String, UserMealCompletionDTO> statuses = userMealCompletionService.getMealCompletionStatuses(userId, date);
            // No need to check if statuses.isEmpty() to return 404, an empty map is a valid 200 OK response.
            logger.info("Returning {} meal status entries for userId: {} on date: {}.", statuses.size(), userId, date);
            return ResponseEntity.ok(statuses);
        } catch (EntityNotFoundException e) {
            logger.warn("EntityNotFoundException while getting meal statuses for userId {}: {}", userId, e.getMessage());
            return ResponseEntity.notFound().build(); 
        } catch (Exception e) {
            logger.error("Error getting meal statuses for userId {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Toggles the completion status of a meal.
     *
     * @param principal The authenticated user details from Spring Security.
     * @param dto       The request body containing date, dayOfWeek, mealType, done status, and mealName.
     * @return The updated UserMealCompletionDTO or an error.
     */
    @PostMapping("/toggle")
    public ResponseEntity<?> toggleMealStatus(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody UserMealCompletionDTO dto) {

        if (principal == null) {
            logger.warn("Attempt to toggle meal status without authentication.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not authenticated."));
        }

        com.example.demo.model.User currentUser = userService.findByEmail(principal.getUsername());
        if (currentUser == null) {
            logger.error("Authenticated principal {} not found as a User entity.", principal.getUsername());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "User authentication error."));
        }
        
        if (dto.getDate() == null || dto.getDayOfWeek() == null || dto.getDayOfWeek().isBlank() ||
            dto.getMealType() == null || dto.getMealType().isBlank()) {
            logger.warn("Invalid DTO received for toggling meal status: {}", dto);
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields in request (date, dayOfWeek, mealType)."));
        }
        // mealName can be null/empty if the slot is being cleared or was empty.

        logger.info("Request to toggle meal status for user: {}, DTO: {}", currentUser.getEmail(), dto);
        try {
            UserMealCompletionDTO updatedCompletionDto = userMealCompletionService.toggleMealCompletionStatus(currentUser.getId(), dto);
            return ResponseEntity.ok(updatedCompletionDto); // Return the full DTO
        } catch (EntityNotFoundException e) {
            logger.warn("EntityNotFoundException while toggling meal status for user {}: {}", currentUser.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException while toggling meal status for user {}: {}", currentUser.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error toggling meal status for user {}: {}", currentUser.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Could not update meal status."));
        }
    }
}
