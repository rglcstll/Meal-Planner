package com.example.demo.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Allergy;
import com.example.demo.service.AllergyService;

@RestController
@RequestMapping("/api/allergies")
public class AllergyController {
    
    @Autowired
    private AllergyService allergyService;
    
    /**
     * Get all available allergies
     */
    @GetMapping
    public ResponseEntity<List<Allergy>> getAllAllergies() {
        return ResponseEntity.ok(allergyService.getAllAllergies());
    }
    
    /**
     * Get allergies for a specific user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Set<Allergy>> getUserAllergies(@PathVariable String userId) {
        return ResponseEntity.ok(allergyService.getUserAllergies(userId));
    }
    
    /**
     * Add an allergy to a user
     */
    @PostMapping("/user/{userId}")
    public ResponseEntity<Void> addAllergyToUser(
            @PathVariable String userId,
            @RequestParam String allergyName) {
        
        allergyService.addAllergyToUser(userId, allergyName);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Remove an allergy from a user
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> removeAllergyFromUser(
            @PathVariable String userId,
            @RequestParam String allergyName) {
        
        allergyService.removeAllergyFromUser(userId, allergyName);
        return ResponseEntity.ok().build();
    }
}