package com.course.controller;

import com.course.model.Course;
import com.course.model.CourseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    // Danh sách Java List tĩnh trong bộ nhớ theo yêu cầu đề bài
    private final List<Course> courseList = new CopyOnWriteArrayList<>();

    public CourseController() {
        // Khởi tạo các khóa học mẫu ban đầu
        courseList.add(new Course(1L, "Spring Boot & Spring Cloud Microservices",
                "Học thiết kế kiến trúc Microservices chuyên sâu", "Nguyen Van A", 199.99));
        courseList.add(new Course(2L, "Spring Security 6 & OAuth2 / JJWT",
                "Bảo mật ứng dụng và Microservices với JWT và Method Security", "Tran Van B", 149.99));
        courseList.add(new Course(3L, "Docker & Kubernetes for Developers",
                "Đóng gói và triển khai ứng dụng container hóa", "Le Van C", 179.99));
    }

    /**
     * API GET /api/courses - Xem danh sách khóa học
     * Phân quyền: Cả STUDENT và INSTRUCTOR đều được phép truy cập
     */
    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT') or hasAuthority('INSTRUCTOR')")
    public ResponseEntity<List<Course>> getAllCourses() {
        return ResponseEntity.ok(courseList);
    }

    /**
     * API GET /api/courses/{id} - Xem chi tiết khóa học
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT') or hasAuthority('INSTRUCTOR')")
    public ResponseEntity<?> getCourseById(@PathVariable Long id) {
        return courseList.stream()
                .filter(course -> course.getId().equals(id))
                .findFirst()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Không tìm thấy khóa học với ID: " + id)));
    }

    /**
     * API POST /api/courses - Tạo khóa học mới
     * Phân quyền: CHỈ CÓ INSTRUCTOR mới được phép tạo khóa học
     */
    @PostMapping
    @PreAuthorize("hasAuthority('INSTRUCTOR')")
    public ResponseEntity<Course> createCourse(@Valid @RequestBody CourseRequest request, Principal principal) {
        long newId = courseList.stream()
                .mapToLong(Course::getId)
                .max()
                .orElse(0L) + 1L;

        String instructorName = (principal != null && principal.getName() != null)
                ? principal.getName()
                : "Giảng viên";

        Course newCourse = new Course(
                newId,
                request.getTitle(),
                request.getDescription(),
                instructorName,
                request.getPrice()
        );

        courseList.add(newCourse);
        return ResponseEntity.status(HttpStatus.CREATED).body(newCourse);
    }
}
