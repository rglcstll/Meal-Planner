package com.example.demo.controller;

import com.example.demo.dto.MealPlanDTO;
import com.example.demo.service.MealPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mealplan")
public class MealPlanController {

    private static final Logger log = LoggerFactory.getLogger(MealPlanController.class);

    private final MealPlanService mealPlanService;

    @Autowired
    public MealPlanController(MealPlanService mealPlanService) {
        this.mealPlanService = mealPlanService;
    }

    @GetMapping
    public ResponseEntity<MealPlanDTO> getMealPlanByDate(@RequestParam String date) {
        log.info("Received request to get meal plan for date: {}", date);
        try {
            LocalDate selectedDate = LocalDate.parse(date);
            MealPlanDTO mealPlan = mealPlanService.getMealPlanForDate(selectedDate);
            if (mealPlan != null) {
                 // Check if the meals map is present and empty, and if ID is null (heuristic for "not found" placeholder)
                 boolean isEffectivelyEmpty = (mealPlan.getMeals() == null || mealPlan.getMeals().isEmpty());

                 if (isEffectivelyEmpty && mealPlan.getId() == null) {
                      log.info("Service returned a DTO that appears to be a 'not found' placeholder for date: {}", date);
                      return ResponseEntity.notFound().build();
                 } else if (isEffectivelyEmpty) {
                     log.info("Saved meal plan for date {} exists but has no meals defined.", date);
                     return ResponseEntity.ok(mealPlan); // Plan exists but is empty
                 }
                 log.info("Returning saved meal plan for date: {}", date);
                 return ResponseEntity.ok(mealPlan);
            } else {
                 log.info("No saved meal plan found (service returned null) for date: {}", date);
                 return ResponseEntity.notFound().build();
            }
        } catch (DateTimeParseException e) {
             log.error("Error parsing date parameter: {} - {}", date, e.getMessage());
             return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
             log.error("Error fetching meal plan for date {}: {}", date, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping
    public ResponseEntity<MealPlanDTO> saveOrUpdateMealPlan(@RequestBody MealPlanDTO mealPlanDto) {
        try {
            log.info("Received MealPlan for saving via POST /mealplan: {}", mealPlanDto);
            if (mealPlanDto == null || mealPlanDto.getDate() == null ) {
                 log.warn("Received meal plan data is incomplete: {}", mealPlanDto);
                 throw new IllegalArgumentException("Received meal plan data is incomplete.");
            }
            MealPlanDTO savedPlan = mealPlanService.saveOrUpdate(mealPlanDto);
            if (savedPlan == null) {
                 log.error("Service failed to save or update meal plan for date {}", mealPlanDto.getDate());
                 return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
            log.info("Successfully saved/updated meal plan for date {}", savedPlan.getDate());
            return ResponseEntity.ok(savedPlan);

        } catch (IllegalArgumentException e) {
            log.error("Error saving meal plan - Invalid data: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("Unexpected error saving meal plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/generate")
    public ResponseEntity<MealPlanDTO> generateMealPlan(
            @RequestParam(value = "goal", defaultValue = "maintain") String weightGoal) {
        log.info("Generating meal plan (basic) with goal: {}. Calling STUB method.", weightGoal);
        try {
            // Calls the renamed STUB method in MealPlanService
            MealPlanDTO mealPlan = mealPlanService.generateBasicMealPlanStub(weightGoal);
            if (mealPlan == null) {
                 log.warn("Basic meal plan generation STUB returned null. Returning empty DTO.");
                 MealPlanDTO emptyDto = new MealPlanDTO(); // Use default constructor
                 emptyDto.setMeals(Collections.emptyMap()); // Set meals to an empty map
                 return ResponseEntity.ok(emptyDto);
            }
            return ResponseEntity.ok(mealPlan);
        } catch (RuntimeException e) {
             log.error("Error during basic meal plan generation (goal: {}): {}", weightGoal, e.getMessage(), e);
             return ResponseEntity.internalServerError().body(null); // Return body as null in case of error
        }
    }

    @GetMapping("/generate/{dietType}")
    public ResponseEntity<Map<String, Map<String, String>>> generateMealPlanWithDiet(
            @PathVariable String dietType,
            @RequestParam(value = "goal", defaultValue = "maintain") String weightGoal) {
        log.info("Generating meal plan with diet: {}, goal: {} (goal may be ignored by service)", dietType, weightGoal);
        try {
            Map<String, Map<String, String>> mealPlanMap = mealPlanService.generateMealPlan(dietType, weightGoal);

            if (mealPlanMap == null || mealPlanMap.isEmpty()) {
                 log.warn("Service returned null or empty map for diet {}, goal {}", dietType, weightGoal);
                 return ResponseEntity.ok(createEmptyPlanMap("Could not generate plan for specified diet."));
            }
            return ResponseEntity.ok(mealPlanMap);

        } catch (Exception e) {
            log.error("Error generating meal plan with diet '{}', goal '{}': {}", dietType, weightGoal, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/generate/{mealsPerDay}")
    public ResponseEntity<MealPlanDTO> generateCustomMealPlan(
            @PathVariable int mealsPerDay,
            @RequestParam(value = "goal", defaultValue = "maintain") String weightGoal) {
        log.info("Attempting to generate meal plan with mealsPerDay: {}, goal: {}. Calling STUB method.", mealsPerDay, weightGoal);
        try {
            // Calls the renamed STUB method in MealPlanService
            MealPlanDTO mealPlan = mealPlanService.generateCustomMealPlanStub(mealsPerDay, weightGoal);
             if (mealPlan == null) {
                 log.warn("Custom meal plan generation STUB (mealsPerDay: {}, goal: {}) returned null. Returning empty DTO.", mealsPerDay, weightGoal);
                 MealPlanDTO emptyDto = new MealPlanDTO(); // Use default constructor
                 emptyDto.setMeals(Collections.emptyMap()); // Set meals to an empty map
                 return ResponseEntity.ok(emptyDto);
            }
            return ResponseEntity.ok(mealPlan);
        } catch (RuntimeException e) {
             log.error("Error during custom meal plan generation (mealsPerDay: {}, goal: {}): {}", mealsPerDay, weightGoal, e.getMessage(), e);
             return ResponseEntity.internalServerError().body(null); // Return body as null in case of error
        }
    }

    private Map<String, Map<String, String>> createEmptyPlanMap(String defaultMessage) {
        Map<String, Map<String, String>> emptyPlan = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        for (String day : days) {
            Map<String, String> dayMeals = new HashMap<>();
            dayMeals.put("BREAKFAST", defaultMessage);
            dayMeals.put("LUNCH", defaultMessage);
            dayMeals.put("DINNER", defaultMessage);
            emptyPlan.put(day.toUpperCase(), dayMeals);
        }
        return emptyPlan;
    }
}
