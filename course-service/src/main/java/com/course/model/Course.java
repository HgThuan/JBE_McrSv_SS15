package com.course.model;

public class Course {

    private Long id;
    private String title;
    private String description;
    private String instructor;
    private Double price;

    public Course() {
    }

    public Course(Long id, String title, String description, String instructor, Double price) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.instructor = instructor;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
