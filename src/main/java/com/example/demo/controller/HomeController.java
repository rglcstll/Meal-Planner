package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // Added for ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal; // Added
import org.springframework.security.core.userdetails.UserDetails; // Added
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import com.example.demo.model.User;
import com.example.demo.service.UserService;
import com.example.demo.service.EmailService;
// import jakarta.servlet.http.HttpSession; // HttpSession not directly used for /api/users/current
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.mail.MessagingException;

import java.util.HashMap; // Added
import java.util.Map;    // Added

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @GetMapping("/")
    public String showHomepage() {
        return "homepage"; // Serves homepage.html
    }

    @GetMapping("/home")
    public String redirectToHomepage() {
        return "redirect:/";
    }

    // Mapping for meal-plans.html
    @GetMapping("/meal-plans.html")
    public String showMealPlansPage() {
        return "meal-plans"; // Assumes meal-plans.html is in src/main/resources/templates/
    }

    // Mapping for recipes.html
    @GetMapping("/recipes.html")
    public String showRecipesPage() {
        return "recipes"; // Assumes recipes.html is in src/main/resources/templates/
    }

    // Mapping for nutrition.html
    @GetMapping("/nutrition.html")
    public String showNutritionPage() {
        return "nutrition"; // Assumes nutrition.html is in src/main/resources/templates/
    }

    // Mapping for about.html
    @GetMapping("/about.html")
    public String showAboutPage() {
        return "about"; // Assumes about.html is in src/main/resources/templates/
    }

    @GetMapping("/user/dashboard")
    public String showDashboard() {
        // Spring Security will handle authentication check
        return "dashboard";
    }

    // --- NEW ENDPOINT TO GET CURRENT USER DETAILS ---
    @GetMapping("/api/users/current")
    @ResponseBody // Ensures the return value is directly bound to the web response body
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails principal) {
        if (principal != null) {
            // The 'principal' object here is what Spring Security provides.
            // It's typically an instance of org.springframework.security.core.userdetails.User.
            // The username is usually the email in your case.
            String email = principal.getUsername();
            com.example.demo.model.User currentUser = userService.findByEmail(email); // Use your service to get the full User entity

            if (currentUser != null) {
                Map<String, Object> userDetailsMap = new HashMap<>();
                userDetailsMap.put("id", currentUser.getId()); // This is the crucial Long ID
                userDetailsMap.put("name", currentUser.getFullName()); // Or getEmail() if preferred for display name
                userDetailsMap.put("email", currentUser.getEmail());
                // Add any other details your frontend might need
                logger.info("Returning current user details for: {}", email);
                return ResponseEntity.ok(userDetailsMap);
            } else {
                logger.warn("Principal found but user not found in database with email: {}", email);
                return ResponseEntity.status(404).body(Map.of("error", "User details not found for authenticated principal."));
            }
        }
        logger.info("No authenticated principal found for /api/users/current");
        // If no principal, it means the user is not authenticated for this API call.
        // Spring Security might intercept this earlier if /api/users/current is secured.
        // If it reaches here, it implies the endpoint might be misconfigured or accessed anonymously.
        return ResponseEntity.status(401).body(Map.of("error", "No authenticated user."));
    }
    // --- END OF NEW ENDPOINT ---


    @GetMapping("/user/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "registration-form";
    }

    @PostMapping("/user/register")
    public String registerUser(@ModelAttribute User user, Model model) {
        try {
            userService.registerUser(user);
            // Assuming emailService.sendConfirmationEmail is correct
            emailService.sendConfirmationEmail(user.getEmail(), user.getVerificationToken());
            model.addAttribute("message", "Registration successful! Please check your email to verify your account.");
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            logger.warn("Registration failed: {}", e.getMessage());
            return "registration-form";
        } catch (MessagingException e) {
            model.addAttribute("error", "Failed to send confirmation email.");
            logger.error("MessagingException during registration: {}", e.getMessage(), e);
            return "registration-form";
        }
    }

    @GetMapping("/user/confirm")
    public String confirmEmail(@RequestParam String token, Model model) {
        try {
            userService.confirmUser(token);
            model.addAttribute("message", "Email verified successfully! You can now log in.");
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @GetMapping("/user/login")
    public String showLoginForm() {
        return "login";
    }


    @GetMapping("/user/logout")
    public String logoutUser() {
        // Spring Security will handle the actual logout
        return "redirect:/";
    }

    @GetMapping("/user/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/user/forgot-password")
    public String processForgotPassword(@RequestParam String email, Model model) {
        try {
            // It's better to call initiatePasswordReset which should find the user
            // and then use the user object returned or handled by that method.
            userService.initiatePasswordReset(email);
            // The token is generated and saved within initiatePasswordReset.
            // You might need to retrieve the user again if you need the token immediately here,
            // or modify initiatePasswordReset to return it or the user.
            // For now, assuming emailService.sendResetPasswordEmail fetches the token itself or it's passed.
            User user = userService.findByEmail(email); // Re-fetch if needed for the token
            if (user != null && user.getResetToken() != null) {
                 emailService.sendResetPasswordEmail(email, user.getResetToken());
                 model.addAttribute("message", "A reset link has been sent to your email.");
            } else {
                // This case might be handled by initiatePasswordReset throwing an exception
                model.addAttribute("error", "Could not initiate password reset for the provided email.");
            }
            return "forgot-password";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "forgot-password";
        } catch (MessagingException e) {
            model.addAttribute("error", "Failed to send reset email.");
            logger.error("MessagingException during forgot password: {}", e.getMessage(), e);
            return "forgot-password";
        }
    }

    @GetMapping("/user/reset-password")
    public String showResetForm(@RequestParam String token, Model model) {
        try {
            userService.validatePasswordResetToken(token);
            model.addAttribute("token", token);
            return "reset-password";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @PostMapping("/user/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Model model) {
        try {
            userService.resetUserPassword(token, newPassword, confirmPassword);
            model.addAttribute("message", "Password successfully reset! Please log in.");
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("token", token);
            return "reset-password";
        }
    }
}
