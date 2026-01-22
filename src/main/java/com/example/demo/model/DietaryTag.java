package com.example.demo.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "dietary_tags")
public class DietaryTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(mappedBy = "dietaryTags")
    private Set<Food> foods = new HashSet<>();

    // Default Constructor (Required by JPA)
    public DietaryTag() {}

    // Constructor with Parameters
    public DietaryTag(String name) {
        this.name = name;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<Food> getFoods() { return foods; }
    public void setFoods(Set<Food> foods) { this.foods = foods; }
}
