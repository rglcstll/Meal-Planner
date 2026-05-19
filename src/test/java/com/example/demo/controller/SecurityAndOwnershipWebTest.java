package com.example.demo.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.SecurityConfig;
import com.example.demo.dto.MealPlanDTO;
import com.example.demo.dto.ShoppingListDTO;
import com.example.demo.model.Allergy;
import com.example.demo.model.User;
import com.example.demo.service.AllergyService;
import com.example.demo.service.MealPlanService;
import com.example.demo.service.ShoppingListService;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {MealPlanController.class, AllergyController.class, ShoppingListController.class})
@Import({SecurityConfig.class, SecurityAndOwnershipWebTest.StubConfig.class})
@ActiveProfiles("test")
class SecurityAndOwnershipWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postMealPlanWithoutCsrfIsForbidden() throws Exception {
        MealPlanDTO dto = new MealPlanDTO();
        dto.setDate("2026-05-19");
        dto.setMeals(Map.of("MONDAY", Map.of("BREAKFAST", "Oatmeal")));

        mockMvc.perform(post("/mealplan")
                .with(user("alice@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isForbidden());
    }

    @Test
    void postMealPlanWithCsrfAndMatchingUserIsAllowed() throws Exception {
        MealPlanDTO dto = new MealPlanDTO();
        dto.setDate("2026-05-19");
        dto.setUserId(1L);
        dto.setMeals(Map.of("MONDAY", Map.of("BREAKFAST", "Oatmeal")));

        mockMvc.perform(post("/mealplan")
                .with(user("alice@example.com"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk());
    }

    @Test
    void getMealPlanWithForeignUserIdIsForbidden() throws Exception {
        mockMvc.perform(get("/mealplan")
                .with(user("alice@example.com"))
                .param("date", "2026-05-19")
                .param("userId", "2"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allergyUserEndpointRejectsForeignUserId() throws Exception {
        mockMvc.perform(post("/api/allergies/user/2")
                .with(user("alice@example.com"))
                .with(csrf())
                .param("allergyName", "Milk"))
            .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        UserService userService() {
            return new UserService(null, null, null) {
                @Override
                public User getUserByEmail(String email) {
                    User user = new User();
                    user.setId(1L);
                    user.setEmail(email);
                    user.setVerified(true);
                    return user;
                }

                @Override
                public User findByEmail(String email) {
                    return getUserByEmail(email);
                }
            };
        }

        @Bean
        MealPlanService mealPlanService() {
            return new MealPlanService(null, null, null, new ObjectMapper(), null) {
                @Override
                public MealPlanDTO saveOrUpdate(MealPlanDTO mealPlanDto, Long authenticatedUserId) {
                    if (mealPlanDto.getUserId() != null && !mealPlanDto.getUserId().equals(authenticatedUserId)) {
                        throw new AccessDeniedException("Forbidden");
                    }
                    mealPlanDto.setUserId(authenticatedUserId);
                    return mealPlanDto;
                }

                @Override
                public MealPlanDTO getMealPlanForDate(Long userId, LocalDate date) {
                    return null;
                }
            };
        }

        @Bean
        AllergyService allergyService() {
            return new AllergyService(null, null, null) {
                @Override
                public Set<Allergy> getUserAllergies(String userIdString) {
                    return Collections.emptySet();
                }

                @Override
                public Set<Allergy> getUserAllergies(Long userId) {
                    return Collections.emptySet();
                }
            };
        }

        @Bean
        ShoppingListService shoppingListService() {
            return new ShoppingListService(null, null, null, new ObjectMapper()) {
                @Override
                public ShoppingListDTO generateShoppingListByMealPlanId(Long mealPlanId, Long authenticatedUserId) {
                    return new ShoppingListDTO();
                }

                @Override
                public ShoppingListDTO generateShoppingListByDate(LocalDate date, Long authenticatedUserId) {
                    return new ShoppingListDTO();
                }
            };
        }
    }
}
