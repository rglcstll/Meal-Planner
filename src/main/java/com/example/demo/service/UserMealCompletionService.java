package com.example.demo.service;

import com.example.demo.dto.UserMealCompletionDTO;
import com.example.demo.model.User;
import com.example.demo.model.UserMealCompletion;
import com.example.demo.repository.UserMealCompletionRepository;
import com.example.demo.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserMealCompletionService {

    private static final Logger logger = LoggerFactory.getLogger(UserMealCompletionService.class);

    private final UserMealCompletionRepository userMealCompletionRepository;
    private final UserRepository userRepository;

    @Autowired
    public UserMealCompletionService(UserMealCompletionRepository userMealCompletionRepository,
                                     UserRepository userRepository) {
        this.userMealCompletionRepository = userMealCompletionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Retrieves all meal completion statuses for a given user and date.
     *
     * @param userId The ID of the user.
     * @param date   The date for which to fetch statuses.
     * @return A Map where keys are "DAYOFWEEK_MEALTYPE" (e.g., "MONDAY_BREAKFAST")
     * and values are UserMealCompletionDTO objects containing done status and mealName.
     */
    @Transactional(readOnly = true)
    public Map<String, UserMealCompletionDTO> getMealCompletionStatuses(Long userId, LocalDate date) {
        logger.info("Fetching meal completion statuses for userId: {} on date: {}", userId, date);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with ID: {} when fetching meal statuses.", userId);
                    return new EntityNotFoundException("User not found with ID: " + userId);
                });

        List<UserMealCompletion> completions = userMealCompletionRepository.findByUserAndCompletionDate(user, date);

        return completions.stream()
                .collect(Collectors.toMap(
                        completion -> completion.getDayOfWeek().toUpperCase() + "_" + completion.getMealType().toUpperCase(),
                        completion -> new UserMealCompletionDTO(
                                completion.getCompletionDate(),
                                completion.getDayOfWeek(),
                                completion.getMealType(),
                                completion.isDone(),
                                completion.getMealName() // Include mealName
                        )
                ));
    }

    /**
     * Toggles the completion status of a specific meal for a user on a specific date.
     * If a record doesn't exist, it creates one.
     *
     * @param userId The ID of the user (ideally obtained from authenticated principal).
     * @param dto    The DTO containing date, dayOfWeek, mealType, new done status, and mealName.
     * @return The updated UserMealCompletionDTO.
     */
    @Transactional
    public UserMealCompletionDTO toggleMealCompletionStatus(Long userId, UserMealCompletionDTO dto) {
        logger.info("Toggling meal completion status for userId: {}, DTO: {}", userId, dto);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with ID: {} when toggling meal status.", userId);
                    return new EntityNotFoundException("User not found with ID: " + userId);
                });

        Optional<UserMealCompletion> existingCompletionOpt = userMealCompletionRepository
                .findByUserAndCompletionDateAndDayOfWeekAndMealType(
                        user,
                        dto.getDate(),
                        dto.getDayOfWeek().toUpperCase(),
                        dto.getMealType().toUpperCase()
                );

        UserMealCompletion completionRecord;
        if (existingCompletionOpt.isPresent()) {
            completionRecord = existingCompletionOpt.get();
            logger.debug("Found existing completion record: {}. Updating status to {} and meal name to '{}'",
                         completionRecord.getId(), dto.isDone(), dto.getMealName());
            completionRecord.setDone(dto.isDone());
            completionRecord.setMealName(dto.getMealName()); // Update mealName
        } else {
            logger.debug("No existing completion record found. Creating new one for user {}, date {}, day {}, type {}, meal '{}', done: {}.",
                         userId, dto.getDate(), dto.getDayOfWeek(), dto.getMealType(), dto.getMealName(), dto.isDone());
            completionRecord = new UserMealCompletion(
                    user,
                    dto.getDate(),
                    dto.getDayOfWeek().toUpperCase(),
                    dto.getMealType().toUpperCase(),
                    dto.isDone(),
                    dto.getMealName() // Set mealName for new record
            );
        }
        UserMealCompletion savedRecord = userMealCompletionRepository.save(completionRecord);
        logger.info("Successfully toggled meal status for record ID: {}, new status: {}, meal: {}",
                    savedRecord.getId(), savedRecord.isDone(), savedRecord.getMealName());
        
        // Convert saved entity back to DTO for the response
        return new UserMealCompletionDTO(
            savedRecord.getCompletionDate(),
            savedRecord.getDayOfWeek(),
            savedRecord.getMealType(),
            savedRecord.isDone(),
            savedRecord.getMealName()
        );
    }
}
