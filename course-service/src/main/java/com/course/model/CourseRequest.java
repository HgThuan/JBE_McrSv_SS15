package com.course.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CourseRequest {

    @NotBlank(message = "Tên khóa học không được để trống")
    private String title;

    @NotBlank(message = "Mô tả khóa học không được để trống")
    private String description;

    @NotNull(message = "Học phí không được để trống")
    @Positive(message = "Học phí phải lớn hơn 0")
    private Double price;

    public CourseRequest() {
    }

    public CourseRequest(String title, String description, Double price) {
        this.title = title;
        this.description = description;
        this.price = price;
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

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
