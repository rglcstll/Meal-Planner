package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserMealCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserMealCompletionRepository extends JpaRepository<UserMealCompletion, Long> {

    // Find all completion statuses for a specific user on a specific date
    List<UserMealCompletion> findByUserAndCompletionDate(User user, LocalDate completionDate);

    // Find a specific meal completion status
    Optional<UserMealCompletion> findByUserAndCompletionDateAndDayOfWeekAndMealType(
            User user,
            LocalDate completionDate,
            String dayOfWeek,
            String mealType
    );
}
