package com.example.demo.model;

public enum MealType {
    BREAKFAST,
    LUNCH,
    DINNER;

    public static MealType fromString(String text) {
        for (MealType type : MealType.values()) {
            if (type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null; // Or throw IllegalArgumentException
    }
}
