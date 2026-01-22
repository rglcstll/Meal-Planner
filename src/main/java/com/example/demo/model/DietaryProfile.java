package com.example.demo.model;

public enum DietaryProfile {
    STANDARD,
    VEGETARIAN,
    VEGAN,
    KETO,
    PALEO,
    GLUTEN_FREE,
    DAIRY_FREE,
    LOW_CARB,
    HIGH_PROTEIN;

    public static DietaryProfile fromString(String text) {
        if (text == null) return STANDARD;
        String normalized = text.trim().replace(" ", "_").toUpperCase();
        for (DietaryProfile profile : DietaryProfile.values()) {
            if (profile.name().equals(normalized) || profile.name().replace("_", "").equals(normalized)) {
                return profile;
            }
        }
        return STANDARD; // Default fallback
    }
}
