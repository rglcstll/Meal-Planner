package com.example.demo.service;

import com.example.demo.model.Allergy;
import com.example.demo.model.Food; // Import Food model
import com.example.demo.model.Recipe;
import com.example.demo.model.User;
import com.example.demo.model.UserAllergy;
import com.example.demo.repository.AllergyRepository;
import com.example.demo.repository.UserAllergyRepository;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher; // Import Matcher
import java.util.regex.Pattern; // Import Pattern
import java.util.stream.Collectors;

@Service
public class AllergyService {

    private static final Logger log = LoggerFactory.getLogger(AllergyService.class);

    private final AllergyRepository allergyRepository;
    private final UserAllergyRepository userAllergyRepository;
    private final UserRepository userRepository;

    @Autowired
    public AllergyService(AllergyRepository allergyRepository,
                          UserAllergyRepository userAllergyRepository,
                          UserRepository userRepository) {
        this.allergyRepository = allergyRepository;
        this.userAllergyRepository = userAllergyRepository;
        this.userRepository = userRepository;
    }

    public List<Allergy> getAllAllergies() {
        return allergyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Set<Allergy> getUserAllergies(String userIdString) {
        if (userIdString == null || userIdString.isBlank() || "default".equalsIgnoreCase(userIdString)) {
            log.info("getUserAllergies called with default or invalid userIdString: '{}'. Returning empty set.", userIdString);
            return Collections.emptySet();
        }
        try {
            Long actualUserId = Long.parseLong(userIdString);
            return getUserAllergies(actualUserId);
        } catch (NumberFormatException e) {
            log.warn("Invalid userIdString format for getUserAllergies: '{}'. Cannot parse to Long. Returning empty set.", userIdString, e);
            return Collections.emptySet();
        }
    }

    @Transactional(readOnly = true)
    public Set<Allergy> getUserAllergies(Long userId) {
        if (userId == null) {
            log.warn("getUserAllergies called with null userId. Returning empty set.");
            return Collections.emptySet();
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found with ID: {} when trying to get allergies. Returning empty set.", userId);
            return Collections.emptySet();
        }
        return userAllergyRepository.findByUser(userOpt.get())
                .map(UserAllergy::getAllergies)
                .orElse(Collections.emptySet());
    }

    @Transactional
    public void addAllergyToUser(String userIdString, String allergyNameInput) {
        if (userIdString == null || userIdString.isBlank() || "default".equalsIgnoreCase(userIdString)) {
            log.warn("Cannot add allergy: invalid userIdString provided: '{}'", userIdString);
            return;
        }
        if (allergyNameInput == null || allergyNameInput.isBlank()) {
            log.warn("Cannot add allergy: allergyName is null or blank for userIdString: '{}'", userIdString);
            return;
        }

        try {
            Long actualUserId = Long.parseLong(userIdString);
            addAllergyToUser(actualUserId, allergyNameInput);
        } catch (NumberFormatException e) {
            log.error("Invalid userIdString format for addAllergyToUser: '{}'. Cannot parse to Long.", userIdString, e);
        }
    }

    @Transactional
    public void addAllergyToUser(Long userId, String allergyNameInput) {
        if (userId == null || allergyNameInput == null || allergyNameInput.isBlank()) {
            log.warn("Cannot add allergy: userId or allergyName is null/blank. UserID: {}, AllergyName: {}", userId, allergyNameInput);
            return;
        }
        String allergyName = allergyNameInput.trim();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {} when trying to add allergy '{}'.", userId, allergyName);
                    return new RuntimeException("User not found with ID: " + userId);
                });

        Allergy allergy = allergyRepository.findByNameIgnoreCase(allergyName)
                .orElseGet(() -> {
                    log.info("Allergy '{}' not found, creating new one.", allergyName);
                    return allergyRepository.save(new Allergy(allergyName));
                });

        UserAllergy userAllergy = userAllergyRepository.findByUser(user)
                .orElseGet(() -> {
                    log.info("No UserAllergy record for user ID {}, creating new.", userId);
                    UserAllergy newUserAllergy = new UserAllergy(user);
                    return userAllergyRepository.save(newUserAllergy);
                });

        if (userAllergy.getAllergies().add(allergy)) {
             userAllergyRepository.save(userAllergy);
             log.info("Successfully added allergy '{}' to user ID {}.", allergy.getName(), userId);
        } else {
            log.info("User ID {} already has allergy '{}'. No changes made.", userId, allergy.getName());
        }
    }


    @Transactional
    public void removeAllergyFromUser(String userIdString, String allergyNameInput) {
         if (userIdString == null || userIdString.isBlank() || "default".equalsIgnoreCase(userIdString)) {
            log.warn("Cannot remove allergy: invalid userIdString provided: '{}'", userIdString);
            return;
        }
         if (allergyNameInput == null || allergyNameInput.isBlank()) {
            log.warn("Cannot remove allergy: allergyName is null or blank for userIdString: '{}'", userIdString);
            return;
        }
        try {
            Long actualUserId = Long.parseLong(userIdString);
            removeAllergyFromUser(actualUserId, allergyNameInput);
        } catch (NumberFormatException e) {
            log.error("Invalid userIdString format for removeAllergyFromUser: '{}'. Cannot parse to Long.", userIdString, e);
        }
    }

    @Transactional
    public void removeAllergyFromUser(Long userId, String allergyNameInput) {
        if (userId == null || allergyNameInput == null || allergyNameInput.isBlank()) {
            log.warn("Cannot remove allergy: userId or allergyName is null/blank. UserID: {}, AllergyName: {}", userId, allergyNameInput);
            return;
        }
        String allergyName = allergyNameInput.trim();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                     log.error("User not found with ID: {} when trying to remove allergy '{}'.", userId, allergyName);
                    return new RuntimeException("User not found with ID: " + userId);
                });

        Optional<Allergy> allergyOpt = allergyRepository.findByNameIgnoreCase(allergyName);
        if (allergyOpt.isEmpty()) {
            log.warn("Attempted to remove non-existent allergy '{}' from user ID {}.", allergyName, userId);
            return;
        }
        Allergy allergyToRemove = allergyOpt.get();

        Optional<UserAllergy> userAllergyOpt = userAllergyRepository.findByUser(user);
        if (userAllergyOpt.isPresent()) {
            UserAllergy userAllergy = userAllergyOpt.get();
            if (userAllergy.getAllergies().remove(allergyToRemove)) {
                if (userAllergy.getAllergies().isEmpty()) {
                    // userAllergyRepository.delete(userAllergy);
                    // log.info("Removed last allergy for user ID {}, UserAllergy entry deleted.", userId);
                } else {
                    userAllergyRepository.save(userAllergy);
                }
                log.info("Successfully removed allergy '{}' from user ID {}.", allergyToRemove.getName(), userId);
            } else {
                log.warn("Allergy '{}' was not found in user ID {}'s allergy list.", allergyToRemove.getName(), userId);
            }
        } else {
            log.warn("UserAllergy entry not found for user ID {}. Cannot remove allergy.", userId);
        }
    }

    /**
     * Gets user allergy names by email.
     */
    @Transactional(readOnly = true)
    public Set<String> getUserAllergyNames(String email) {
        if (email == null || email.isBlank()) {
            log.debug("getUserAllergyNames called with null/blank email. Returning empty set.");
            return Collections.emptySet();
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.debug("User not found with email: {}. Returning empty allergy set.", email);
            return Collections.emptySet();
        }

        return userAllergyRepository.findByUser(user)
                .map(userAllergy -> userAllergy.getAllergies().stream()
                        .map(Allergy::getName)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    @Transactional(readOnly = true)
    public List<Recipe> filterRecipesByAllergies(List<Recipe> recipesToFilter, String userIdString) {
        if (userIdString == null || userIdString.isBlank() || "default".equalsIgnoreCase(userIdString)) {
             log.info("filterRecipesByAllergies called with default or invalid userIdString: '{}'. Returning all recipes.", userIdString);
            return recipesToFilter;
        }
         try {
            Long actualUserId = Long.parseLong(userIdString);
            return filterRecipesByAllergies(recipesToFilter, actualUserId);
        } catch (NumberFormatException e) {
            log.error("Invalid userIdString format for filtering recipes: '{}'. Cannot parse to Long. Returning unfiltered recipes.", userIdString, e);
            return recipesToFilter;
        }
    }

    @Transactional(readOnly = true)
    public List<Recipe> filterRecipesByAllergies(List<Recipe> recipesToFilter, Long userId) {
        if (userId == null) {
             log.info("filterRecipesByAllergies called with null userId. Returning all recipes.");
            return recipesToFilter;
        }
        Set<Allergy> userAllergiesSet = getUserAllergies(userId);

        if (userAllergiesSet.isEmpty()) {
            log.info("User ID {} has no allergies, returning all {} recipes.", userId, recipesToFilter.size());
            return recipesToFilter;
        }

        // Compile patterns for each user allergen for efficient regex matching
        List<Pattern> userAllergenPatterns = userAllergiesSet.stream()
                .map(allergy -> Pattern.compile("\\b" + Pattern.quote(allergy.getName().toLowerCase()) + "\\b"))
                .collect(Collectors.toList());

        log.info("Filtering {} recipes for user ID {} with allergies: {}",
                 recipesToFilter.size(), userId, userAllergiesSet.stream().map(Allergy::getName).collect(Collectors.toSet()));

        return recipesToFilter.stream()
                .filter(recipe -> {
                    // 1. Check recipe's explicitly declared allergens
                    if (recipe.getAllergens() != null && !recipe.getAllergens().isEmpty()) {
                        Set<String> recipeDeclaredAllergensLower = recipe.getAllergens().stream()
                                                                        .map(String::toLowerCase)
                                                                        .collect(Collectors.toSet());
                        for (Allergy userAllergy : userAllergiesSet) {
                            if (recipeDeclaredAllergensLower.contains(userAllergy.getName().toLowerCase())) {
                                log.debug("Recipe '{}' (ID: {}) filtered out for user ID {} due to declared allergen: {}",
                                          recipe.getName(), recipe.getId(), userId, userAllergy.getName());
                                return false; // Filter out (has conflict with declared allergen)
                            }
                        }
                    }

                    // 2. Check recipe's ingredient names against user's allergen patterns
                    if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
                        for (Food ingredient : recipe.getIngredients()) {
                            if (ingredient != null && ingredient.getName() != null) {
                                String ingredientNameLower = ingredient.getName().toLowerCase();
                                for (int i = 0; i < userAllergenPatterns.size(); i++) {
                                    Pattern allergenPattern = userAllergenPatterns.get(i);
                                    Matcher matcher = allergenPattern.matcher(ingredientNameLower);
                                    if (matcher.find()) {
                                        // Retrieve the original allergen name for logging
                                        String originalAllergenName = "";
                                        int k=0;
                                        for(Allergy ua : userAllergiesSet){
                                            if(k==i) {
                                                originalAllergenName = ua.getName();
                                                break;
                                            }
                                            k++;
                                        }
                                        log.debug("Recipe '{}' (ID: {}) filtered out for user ID {} due to ingredient '{}' matching user allergen '{}' (via regex)",
                                                  recipe.getName(), recipe.getId(), userId, ingredient.getName(), originalAllergenName);
                                        return false; // Filter out (has conflict with ingredient name)
                                    }
                                }
                            }
                        }
                    }
                    return true; // Keep recipe (no conflict found)
                })
                .collect(Collectors.toList());
    }
}
