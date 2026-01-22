package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.security.web.csrf.CookieCsrfTokenRepository; // Example for CSRF

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF Configuration:
            // KEEP DISABLED FOR NOW to simplify Tomcat deployment troubleshooting.
            // For production, you SHOULD enable CSRF. If you do, ensure Thymeleaf forms
            // include the CSRF token (usually automatic) and AJAX POST/PUT/DELETE requests
            // also include the token in headers.
            .csrf((csrf) -> csrf.disable()) // Keep CSRF disabled for initial troubleshooting

            .authorizeHttpRequests((authorize) -> authorize
                // Publicly accessible paths
                .requestMatchers(
                    "/",
                    "/home",
                    "/meal-plans.html", // Added
                    "/recipes.html",    // Added
                    "/nutrition.html",  // Added
                    "/about.html",      // Added
                    "/user/register",
                    "/user/confirm", // For email confirmation
                    "/user/login",   // Login page itself
                    "/user/forgot-password",
                    "/user/reset-password",
                    "/css/**",       // Static resources
                    "/js/**",
                    "/static/**",    // General static content
                    "/images/**",    // If you have an images folder
                    "/api/public/**" // Any public API endpoints
                ).permitAll()
                // Secured paths - require authentication
                .requestMatchers(
                    "/user/dashboard",
                    "/api/users/current", // Endpoint to get current user details
                    "/api/allergies/**",  // Allergy management
                    "/api/foods/**",      // Food and meal plan generation
                    "/api/recipes/**",    // Recipe management
                    "/mealplan/**",       // Meal plan saving/loading (if distinct from /api/foods)
                    "/api/mealstatus/**"  // Meal completion status
                ).authenticated()
                .anyRequest().authenticated() // All other unspecified requests require authentication
            )
            .formLogin((form) -> form
                .loginPage("/user/login")           // Custom login page URL
                .loginProcessingUrl("/user/login")  // URL Spring Security will process the login form submission
                .defaultSuccessUrl("/user/dashboard", true) // Redirect to dashboard on successful login
                .failureUrl("/user/login?error=true")       // Redirect on login failure
                .usernameParameter("email")         // HTML input name for email/username
                .passwordParameter("password")      // HTML input name for password
                .permitAll()                        // Allow access to the login page and processing URL for all
            )
            .logout((logout) -> logout
                .logoutUrl("/user/logout")          // URL to trigger logout
                .logoutSuccessUrl("/user/login?logout=true") // Redirect after logout
                .invalidateHttpSession(true)        // Invalidate the HTTP session
                .clearAuthentication(true)          // Clear the authentication information
                .deleteCookies("JSESSIONID")        // Optionally delete cookies
                .permitAll()                        // Allow access to logout URL for all
            );

        return http.build();
    }
}
