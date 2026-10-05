package com.course;

import com.course.model.CourseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourseServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. STUDENT gọi GET /api/courses -> 200 OK")
    void testStudentCanGetAllCourses() throws Exception {
        mockMvc.perform(get("/api/courses")
                        .header("X-User-Role", "STUDENT")
                        .header("X-User-Name", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("2. INSTRUCTOR gọi GET /api/courses -> 200 OK")
    void testInstructorCanGetAllCourses() throws Exception {
        mockMvc.perform(get("/api/courses")
                        .header("X-User-Role", "INSTRUCTOR")
                        .header("X-User-Name", "instructor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("3. STUDENT gọi POST /api/courses -> 403 Forbidden (Yêu cầu đề bài)")
    void testStudentCannotCreateCourse_Forbidden403() throws Exception {
        CourseRequest newCourse = new CourseRequest(
                "Khóa học trái phép của Student",
                "Mô tả không được phép",
                99.0
        );

        mockMvc.perform(post("/api/courses")
                        .header("X-User-Role", "STUDENT")
                        .header("X-User-Name", "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCourse)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này! Thao tác yêu cầu quyền INSTRUCTOR."));
    }

    @Test
    @DisplayName("4. INSTRUCTOR gọi POST /api/courses -> 201 Created")
    void testInstructorCanCreateCourse_Success201() throws Exception {
        CourseRequest newCourse = new CourseRequest(
                "Java Backend Masterclass",
                "Chuyên sâu về Spring Boot, Security và Microservices",
                299.99
        );

        mockMvc.perform(post("/api/courses")
                        .header("X-User-Role", "INSTRUCTOR")
                        .header("X-User-Name", "instructor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCourse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Java Backend Masterclass"))
                .andExpect(jsonPath("$.instructor").value("instructor"));
    }

    @Test
    @DisplayName("5. Không có Header X-User-Role -> 401 Unauthorized")
    void testMissingHeader_Unauthorized401() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
