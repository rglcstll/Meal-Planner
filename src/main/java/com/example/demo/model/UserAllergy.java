package com.example.demo.model;

import java.util.HashSet;
import java.util.Set;
import java.util.Objects; // For equals and hashCode

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne; // Added
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint; // For unique user constraint

@Entity
@Table(name = "user_allergies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id"}) // Ensure one UserAllergy entry per user
})
public class UserAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- UPDATED: Changed from String userId to User user ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // Foreign key to the User table
    private User user;

    @ManyToMany(fetch = FetchType.EAGER) // Eager fetch might be fine if the set of allergies per user is small
    @JoinTable(
        name = "user_allergy_mapping",
        joinColumns = @JoinColumn(name = "user_allergy_id"),
        inverseJoinColumns = @JoinColumn(name = "allergy_id")
    )
    private Set<Allergy> allergies = new HashSet<>();

    // Default constructor
    public UserAllergy() {
    }

    // --- UPDATED: Constructor takes User object ---
    public UserAllergy(User user) {
        this.user = user;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // --- UPDATED: Getter and Setter for User object ---
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Set<Allergy> getAllergies() {
        return allergies;
    }

    public void setAllergies(Set<Allergy> allergies) {
        this.allergies = allergies != null ? new HashSet<>(allergies) : new HashSet<>();
    }

    public void addAllergy(Allergy allergy) {
        if (this.allergies == null) {
            this.allergies = new HashSet<>();
        }
        this.allergies.add(allergy);
    }

    public boolean removeAllergy(Allergy allergy) {
        if (this.allergies != null) {
            return this.allergies.remove(allergy);
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAllergy that = (UserAllergy) o;
        // If using database-generated IDs, equality relies on them.
        // Before persistence, equality relies on the User object if present.
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return Objects.equals(user, that.user); // Compare by user if IDs are null
    }

    @Override
    public int hashCode() {
        // Use id if available; otherwise, rely on the User object's hashcode.
        return id != null ? Objects.hash(id) : Objects.hash(user);
    }

    @Override
    public String toString() {
        return "UserAllergy{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : "null") +
                ", allergiesCount=" + (allergies != null ? allergies.size() : 0) +
                '}';
    }
}