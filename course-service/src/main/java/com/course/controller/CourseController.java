package com.course.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCourses(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        List<Map<String, Object>> courses = List.of(
                Map.of("id", 1, "title", "Spring Boot & Spring Cloud Microservices", "instructor", "Nguyen Van A"),
                Map.of("id", 2, "title", "Spring Security 6 & OAuth2 / JJWT", "instructor", "Tran Van B"),
                Map.of("id", 3, "title", "Docker & Kubernetes for Developers", "instructor", "Le Van C")
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Lấy danh sách khóa học thành công từ Course-Service!");
        response.put("xUserRole", userRole);
        response.put("xUserName", userName);
        response.put("totalCourses", courses.size());
        response.put("courses", courses);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCourseById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("title", "Chi tiết khóa học #" + id);
        response.put("xUserRole", userRole);
        response.put("xUserName", userName);

        return ResponseEntity.ok(response);
    }
}
