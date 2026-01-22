package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAllergyRepository extends JpaRepository<UserAllergy, Long> {
    // Method to find by the User entity
    Optional<UserAllergy> findByUser(User user);

    // Method to find by the user's ID (Long)
    Optional<UserAllergy> findByUserId(Long userId);
}