package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
// import java.util.Set; // No longer directly storing Allergy entities here

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // Will be encoded before saving

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false, length = 50)
    private String activityLevel;

    @Column(length = 255)
    private String dietPreference;

    // --- REMOVED this field ---
    // @Column(length = 255)
    // private String allergies;

    // Optional: If you want a direct link from User to its UserAllergy entry.
    // This is optional as UserAllergy already links back to User.
    // @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    // private UserAllergy userAllergyEntry;


    @Column(unique = true)
    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    @Column(unique = true)
    private String verificationToken;

    @Column(nullable = false)
    private boolean verified = false;

    public User() {}

    public User(String fullName, String email, String password, Integer age,
                String gender, Integer height, Integer weight, String activityLevel) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.age = age;
        this.gender = gender;
        this.height = height;
        this.weight = weight;
        this.activityLevel = activityLevel;
        this.verified = false;
    }

    // Getters and Setters for existing fields (omitted for brevity, keep them as they are)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }
    public String getDietPreference() { return dietPreference; }
    public void setDietPreference(String dietPreference) { this.dietPreference = dietPreference; }

    // --- REMOVED getter and setter for the old String allergies field ---
    // public String getAllergies() { return allergies; }
    // public void setAllergies(String allergies) { this.allergies = allergies; }


    // Getters and setters for token fields (omitted for brevity, keep them)
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public boolean isResetTokenValid() {
        return resetToken != null && resetTokenExpiry != null && resetTokenExpiry.isAfter(LocalDateTime.now());
    }
    public void clearResetToken() {
        this.resetToken = null;
        this.resetTokenExpiry = null;
    }
    public void clearVerificationToken() {
        this.verificationToken = null;
    }

    // Optional: Getter/Setter if you added userAllergyEntry
    // public UserAllergy getUserAllergyEntry() { return userAllergyEntry; }
    // public void setUserAllergyEntry(UserAllergy userAllergyEntry) { this.userAllergyEntry = userAllergyEntry; }
}