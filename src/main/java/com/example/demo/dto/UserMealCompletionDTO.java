package com.example.demo.dto;

import java.time.LocalDate;

public class UserMealCompletionDTO {
    private LocalDate date;
    private String dayOfWeek;
    private String mealType;
    private boolean done;
    private String mealName; // Added field

    // Constructors
    public UserMealCompletionDTO() {
    }

    // Updated constructor
    public UserMealCompletionDTO(LocalDate date, String dayOfWeek, String mealType, boolean done, String mealName) {
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.mealType = mealType;
        this.done = done;
        this.mealName = mealName; // Initialize mealName
    }

    // Getters and Setters
    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getMealName() { // Getter for mealName
        return mealName;
    }

    public void setMealName(String mealName) { // Setter for mealName
        this.mealName = mealName;
    }

    @Override
    public String toString() {
        return "UserMealCompletionDTO{" +
                "date=" + date +
                ", dayOfWeek='" + dayOfWeek + '\'' +
                ", mealType='" + mealType + '\'' +
                ", done=" + done +
                ", mealName='" + mealName + '\'' + // Added mealName
                '}';
    }
}
