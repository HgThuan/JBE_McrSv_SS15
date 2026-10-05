package com.course;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourseServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/courses trả về danh sách khóa học và phản ánh Header X-User-Role")
    void testGetCourses_WithHeader() throws Exception {
        mockMvc.perform(get("/api/courses")
                        .header("X-User-Role", "STUDENT")
                        .header("X-User-Name", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xUserRole").value("STUDENT"))
                .andExpect(jsonPath("$.xUserName").value("student"))
                .andExpect(jsonPath("$.courses").isArray());
    }
}
