package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.dto.MealPlanDTO;
import com.example.demo.model.MealPlan;
import com.example.demo.model.User;
import com.example.demo.repository.MealPlanRepository;
import com.example.demo.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MealPlanServiceDataIntegrityTest {

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MealPlanRepository mealPlanRepository;

    @Test
    void saveOrUpdateUsesSingleRecordPerUserPerDate() {
        User user = createUser("alice@example.com");

        MealPlanDTO first = new MealPlanDTO();
        first.setDate("2026-05-19");
        first.setUserId(user.getId());
        first.setMeals(Map.of("MONDAY", Map.of("BREAKFAST", "Oatmeal")));
        MealPlanDTO savedFirst = mealPlanService.saveOrUpdate(first, user.getId());

        MealPlanDTO second = new MealPlanDTO();
        second.setDate("2026-05-19");
        second.setUserId(user.getId());
        second.setMeals(Map.of("MONDAY", Map.of("BREAKFAST", "Scrambled Eggs")));
        MealPlanDTO savedSecond = mealPlanService.saveOrUpdate(second, user.getId());

        MealPlan persisted = mealPlanRepository.findByUserIdAndDate(user.getId(), java.time.LocalDate.parse("2026-05-19"))
            .orElseThrow();

        assertEquals(savedFirst.getId(), savedSecond.getId(), "Expected update of the same row for same user/date.");
        assertEquals(savedSecond.getId(), persisted.getId());
        assertEquals(1L, mealPlanRepository.findByUserEmail("alice@example.com").size());
    }

    @Test
    void saveOrUpdateRejectsForeignUserPayload() {
        User user = createUser("owner@example.com");
        User other = createUser("other@example.com");

        MealPlanDTO dto = new MealPlanDTO();
        dto.setDate("2026-05-19");
        dto.setUserId(other.getId());
        dto.setMeals(Map.of("MONDAY", Map.of("BREAKFAST", "Oatmeal")));

        assertThrows(AccessDeniedException.class, () -> mealPlanService.saveOrUpdate(dto, user.getId()));
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setAge(30);
        user.setGender("male");
        user.setHeight(175);
        user.setWeight(75);
        user.setActivityLevel("moderate");
        user.setVerified(true);
        return userRepository.save(user);
    }
}
