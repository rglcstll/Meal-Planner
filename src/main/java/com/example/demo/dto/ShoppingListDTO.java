package com.example.demo.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for Shopping List.
 * Aggregates ingredients from a meal plan, grouped by category.
 */
public class ShoppingListDTO {

    private Long mealPlanId;
    private String mealPlanDate;

    // Category -> List of ShoppingItem
    private Map<String, List<ShoppingItem>> itemsByCategory;

    // Total count of unique items
    private int totalItems;

    // Estimated total cost (optional)
    private double estimatedCost;

    // Default constructor
    public ShoppingListDTO() {
    }

    // Constructor with all fields
    public ShoppingListDTO(Long mealPlanId, String mealPlanDate,
                          Map<String, List<ShoppingItem>> itemsByCategory,
                          int totalItems, double estimatedCost) {
        this.mealPlanId = mealPlanId;
        this.mealPlanDate = mealPlanDate;
        this.itemsByCategory = itemsByCategory;
        this.totalItems = totalItems;
        this.estimatedCost = estimatedCost;
    }

    // Getters and Setters
    public Long getMealPlanId() {
        return mealPlanId;
    }

    public void setMealPlanId(Long mealPlanId) {
        this.mealPlanId = mealPlanId;
    }

    public String getMealPlanDate() {
        return mealPlanDate;
    }

    public void setMealPlanDate(String mealPlanDate) {
        this.mealPlanDate = mealPlanDate;
    }

    public Map<String, List<ShoppingItem>> getItemsByCategory() {
        return itemsByCategory;
    }

    public void setItemsByCategory(Map<String, List<ShoppingItem>> itemsByCategory) {
        this.itemsByCategory = itemsByCategory;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShoppingListDTO that = (ShoppingListDTO) o;
        return totalItems == that.totalItems &&
               Double.compare(that.estimatedCost, estimatedCost) == 0 &&
               Objects.equals(mealPlanId, that.mealPlanId) &&
               Objects.equals(mealPlanDate, that.mealPlanDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mealPlanId, mealPlanDate, totalItems, estimatedCost);
    }

    @Override
    public String toString() {
        return "ShoppingListDTO{" +
               "mealPlanId=" + mealPlanId +
               ", mealPlanDate='" + mealPlanDate + '\'' +
               ", totalItems=" + totalItems +
               ", estimatedCost=" + estimatedCost +
               ", categories=" + (itemsByCategory != null ? itemsByCategory.size() : 0) +
               '}';
    }

    /**
     * Inner class representing a single shopping item.
     */
    public static class ShoppingItem {
        private String name;
        private int quantity;
        private String unit;
        private double estimatedPrice;
        private boolean checked;

        public ShoppingItem() {
        }

        public ShoppingItem(String name, int quantity, String unit, double estimatedPrice) {
            this.name = name;
            this.quantity = quantity;
            this.unit = unit;
            this.estimatedPrice = estimatedPrice;
            this.checked = false;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public double getEstimatedPrice() {
            return estimatedPrice;
        }

        public void setEstimatedPrice(double estimatedPrice) {
            this.estimatedPrice = estimatedPrice;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShoppingItem that = (ShoppingItem) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "ShoppingItem{" +
                   "name='" + name + '\'' +
                   ", quantity=" + quantity +
                   ", unit='" + unit + '\'' +
                   ", estimatedPrice=" + estimatedPrice +
                   '}';
        }
    }
}
