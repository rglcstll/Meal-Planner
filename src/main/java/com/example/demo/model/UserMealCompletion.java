package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "user_meal_completions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "completion_date", "day_of_week", "meal_type"})
})
public class UserMealCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @Column(name = "day_of_week", nullable = false, length = 10) // MONDAY, TUESDAY, etc.
    private String dayOfWeek;

    @Column(name = "meal_type", nullable = false, length = 10) // BREAKFAST, LUNCH, DINNER
    private String mealType;

    @Column(name = "is_done", nullable = false)
    private boolean isDone;

    @Column(name = "meal_name", nullable = true, length = 255) // Added: Store the name of the completed meal
    private String mealName;

    // Constructors
    public UserMealCompletion() {
    }

    // Updated constructor to include mealName
    public UserMealCompletion(User user, LocalDate completionDate, String dayOfWeek, String mealType, boolean isDone, String mealName) {
        this.user = user;
        this.completionDate = completionDate;
        this.dayOfWeek = dayOfWeek;
        this.mealType = mealType;
        this.isDone = isDone;
        this.mealName = mealName; // Initialize mealName
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public boolean isDone() {
        return isDone;
    }

    public void setDone(boolean done) {
        isDone = done;
    }

    public String getMealName() { // Getter for mealName
        return mealName;
    }

    public void setMealName(String mealName) { // Setter for mealName
        this.mealName = mealName;
    }

    // equals and hashCode: Consider if mealName should be part of equality.
    // For the unique constraint, it's based on user, date, day, and type.
    // If mealName changes for an existing slot, it's an update to that record.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMealCompletion that = (UserMealCompletion) o;
        return Objects.equals(id, that.id) && // Primary key is best for JPA entities
               Objects.equals(user, that.user) &&
               Objects.equals(completionDate, that.completionDate) &&
               Objects.equals(dayOfWeek, that.dayOfWeek) &&
               Objects.equals(mealType, that.mealType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, user, completionDate, dayOfWeek, mealType);
    }

    @Override
    public String toString() {
        return "UserMealCompletion{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : "null") +
                ", completionDate=" + completionDate +
                ", dayOfWeek='" + dayOfWeek + '\'' +
                ", mealType='" + mealType + '\'' +
                ", isDone=" + isDone +
                ", mealName='" + mealName + '\'' + // Added mealName
                '}';
    }
}
