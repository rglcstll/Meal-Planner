package com.example.demo.repository;

import com.example.demo.model.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Import Optional

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, Long> {
    // Find by name (case-insensitive is good for lookups)
    Optional<Allergy> findByNameIgnoreCase(String name);

    // Keep existing findByName if you still use it elsewhere, but prefer case-insensitive for lookups
    Allergy findByName(String name); // This was in your original AllergyService
}