package com.example.demo.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Allergy;
import com.example.demo.service.AllergyService;
import com.example.demo.service.UserService;

@RestController
@RequestMapping("/api/allergies")
public class AllergyController {
    
    @Autowired
    private AllergyService allergyService;

    @Autowired
    private UserService userService;
    
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
    public ResponseEntity<Set<Allergy>> getUserAllergies(@PathVariable String userId,
                                                         @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
        if (!authenticatedUserId.toString().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(allergyService.getUserAllergies(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<Set<Allergy>> getMyAllergies(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
        return ResponseEntity.ok(allergyService.getUserAllergies(authenticatedUserId));
    }
    
    /**
     * Add an allergy to a user
     */
    @PostMapping("/user/{userId}")
    public ResponseEntity<Void> addAllergyToUser(
            @PathVariable String userId,
            @RequestParam String allergyName,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
        if (!authenticatedUserId.toString().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        allergyService.addAllergyToUser(userId, allergyName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me")
    public ResponseEntity<Void> addAllergyToCurrentUser(@RequestParam String allergyName,
                                                        @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
        allergyService.addAllergyToUser(authenticatedUserId, allergyName);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Remove an allergy from a user
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> removeAllergyFromUser(
            @PathVariable String userId,
            @RequestParam String allergyName,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
        if (!authenticatedUserId.toString().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        allergyService.removeAllergyFromUser(userId, allergyName);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> removeAllergyFromCurrentUser(@RequestParam String allergyName,
                                                             @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long authenticatedUserId = userService.getUserByEmail(principal.getUsername()).getId();
        allergyService.removeAllergyFromUser(authenticatedUserId, allergyName);
        return ResponseEntity.ok().build();
    }
}
