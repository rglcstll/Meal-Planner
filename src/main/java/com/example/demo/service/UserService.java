package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.model.Allergy; // Import Allergy model
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder; // Ensure PasswordEncoder is imported
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // Keep if used elsewhere, though not in the provided getAllergies

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Import MailUtil2 and EmailService if you use them for email operations
// import com.example.demo.mail.MailUtil2;
// import com.example.demo.service.EmailService; // Assuming you have an EmailService

@Service
public class UserService implements UserDetailsService { // Implement UserDetailsService if not already

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AllergyService allergyService; // Inject AllergyService

    // Assuming you might have an EmailService for sending emails
    // private final EmailService emailService;

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AllergyService allergyService
                       /* EmailService emailService */) { // Add AllergyService to constructor
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.allergyService = allergyService;
        // this.emailService = emailService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
        if (!user.isVerified()) {
            // Consider if this exception is appropriate or if you handle unverified users differently
            throw new UsernameNotFoundException("Email not verified. Please check your email to verify your account.");
        }
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            user.isVerified(), // Use the actual verified status
            true, // accountNonExpired
            true, // credentialsNonExpired
            true, // accountNonLocked
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")) // Or fetch roles from User entity
        );
    }

    @Transactional
    public User registerUser(User user /*, String siteURL */) { // siteURL for verification email
        if (userRepository.findByEmail(user.getEmail()) != null) {
            // Changed RuntimeException to IllegalArgumentException
            throw new IllegalArgumentException("This email is already registered. Please use a different email or login.");
        }
        // Validate other user fields if necessary
        validateUserFields(user); // Example validation

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setVerified(false); // Default to not verified

        String randomCode = UUID.randomUUID().toString();
        user.setVerificationToken(randomCode);
        // user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24)); // Optional: token expiry

        User savedUser = userRepository.save(user);

        // Example: Send verification email
        // try {
        //     if (emailService != null && siteURL != null) {
        //         emailService.sendVerificationEmail(savedUser, siteURL);
        //     }
        // } catch (Exception e) {
        //     // Log error, but don't necessarily fail registration
        //     // log.error("Error sending verification email to {}: {}", savedUser.getEmail(), e.getMessage());
        // }
        return savedUser;
    }

    private void validateUserFields(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User object cannot be null.");
        }
        if (!StringUtils.hasText(user.getFullName())) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            // This check might be redundant if password encoding happens before this
            throw new IllegalArgumentException("Password is required.");
        }
        // Add other validations as needed (age, gender, etc.)
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findByResetToken(String token) {
        return userRepository.findByResetToken(token);
    }

    public User findByVerificationToken(String token) {
        return userRepository.findByVerificationToken(token);
    }

    @Transactional
    public void confirmUser(String token) {
        User user = userRepository.findByVerificationToken(token);
        if (user == null /* || (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) */) {
            throw new IllegalArgumentException("Invalid or expired verification link.");
        }
        user.setVerified(true);
        user.clearVerificationToken();
        userRepository.save(user);
    }


    @Transactional
    public void initiatePasswordReset(String email /*, String siteURL */) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("Email not found in our records.");
            // Or simply log and return to avoid exposing whether an email exists
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour
        userRepository.save(user);

        // Example: Send password reset email
        // try {
        //     if (emailService != null && siteURL != null) {
        //         emailService.sendPasswordResetEmail(user, siteURL);
        //     }
        // } catch (Exception e) {
        //    // log.error("Error sending password reset email to {}: {}", user.getEmail(), e.getMessage());
        // }
    }

    @Transactional
    public void validatePasswordResetToken(String token) {
        User user = userRepository.findByResetToken(token);
        if (user == null || !user.isResetTokenValid()) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }
    }

    @Transactional
    public void resetUserPassword(String token, String newPassword, String confirmPassword) {
        // It's good practice to re-validate the token here, even if validated on a previous step
        User user = userRepository.findByResetToken(token);
        if (user == null || !user.isResetTokenValid()) {
            throw new IllegalArgumentException("Invalid or expired reset token. Cannot reset password.");
        }
        if (!StringUtils.hasText(newPassword) || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match or are empty.");
        }
        // Add password strength validation if needed

        user.setPassword(passwordEncoder.encode(newPassword));
        user.clearResetToken();
        userRepository.save(user);
    }

    public User authenticate(String email, String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Email and password are required.");
        }
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return null; // User not found
        }
        if (!user.isVerified()) {
            // Potentially throw a specific exception or return a status indicating not verified
            return null; // Or handle as an authentication failure
        }
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user; // Authentication successful
        }
        return null; // Password mismatch
    }


    /**
     * Gets the user's allergies as a comma-separated string.
     * @param email The email of the user.
     * @return A string of allergies, or an empty string if none or user not found.
     */
    public String getAllergies(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            // log.warn("User not found for email: {} when trying to get allergies.", email);
            return ""; // Return empty string if user not found
        }

        // Use AllergyService to get the Set<Allergy>
        // Assumes User.getId() returns Long, and AllergyService.getUserAllergies(Long userId) exists
        // The AllergyService provided earlier has an overloaded getUserAllergies(String userIdString)
        // which can parse the ID if needed, or you can directly call getUserAllergies(Long userId).
        Set<Allergy> userAllergies = allergyService.getUserAllergies(user.getId()); // Pass Long ID

        if (userAllergies == null || userAllergies.isEmpty()) {
            return ""; // Return empty string if no allergies
        }

        // Convert the Set<Allergy> to a comma-separated string of names
        return userAllergies.stream()
                            .map(Allergy::getName)
                            .filter(Objects::nonNull) // Ensure names are not null before joining
                            .collect(Collectors.joining(", "));
    }

    public String getDietPreference(String email) {
        User user = userRepository.findByEmail(email);
        // Return empty string or a default if preference is null, to avoid NPEs downstream
        return (user != null && user.getDietPreference() != null) ? user.getDietPreference() : "";
    }

    public User getUserByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
        return user;
    }
}