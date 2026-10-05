package com.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identity.dto.request.LoginRequest;
import com.identity.model.RefreshToken;
import com.identity.repository.RefreshTokenRepository;
import com.identity.repository.UserRepository;
import com.identity.security.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("1. Đăng nhập thành công với tài khoản STUDENT -> Trả về Access Token và Refresh Token")
    void testLoginSuccessStudent() throws Exception {
        LoginRequest loginRequest = new LoginRequest("student", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn();

        String responseString = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseString);
        String accessToken = jsonNode.get("accessToken").asText();
        String refreshToken = jsonNode.get("refreshToken").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
    }

    @Test
    @DisplayName("2. Kiểm tra Payload Access Token chứa đủ sub (username), role, jti và thời gian sống 15 phút")
    void testJwtPayloadClaims() throws Exception {
        LoginRequest loginRequest = new LoginRequest("student", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.get("accessToken").asText();

        // Kiểm tra tính hợp lệ của token
        assertTrue(jwtUtils.validateJwtToken(accessToken), "Access token phải hợp lệ");

        // Parse claims từ JWT
        Claims claims = jwtUtils.parseClaims(accessToken);

        // 1. Kiểm tra Standard Claim 'sub' (username)
        assertEquals("student", claims.getSubject(), "Claim 'sub' phải là username 'student'");

        // 2. Kiểm tra Private Claim 'role'
        assertEquals("STUDENT", claims.get("role"), "Private claim 'role' phải là 'STUDENT'");

        // 3. Bắt buộc có claim 'jti' (JWT ID - mã định danh duy nhất của token)
        String jti = claims.getId() != null ? claims.getId() : (String) claims.get("jti");
        assertNotNull(jti, "Bắt buộc có claim 'jti'");
        final String finalJti = jti;
        assertDoesNotThrow(() -> UUID.fromString(finalJti), "Claim 'jti' phải là một UUID hợp lệ");

        // 4. Kiểm tra thời gian hết hạn (15 phút = 900.000 ms)
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        assertNotNull(issuedAt);
        assertNotNull(expiration);
        long diffMs = expiration.getTime() - issuedAt.getTime();
        assertEquals(900000L, diffMs, "Thời gian sống của Access Token phải là 15 phút (900.000 ms)");
    }

    @Test
    @DisplayName("3. Kiểm tra Refresh Token được lưu trong bảng refresh_tokens với hạn sống 7 ngày")
    void testRefreshTokenSavedInDatabase() throws Exception {
        LoginRequest loginRequest = new LoginRequest("student", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String refreshTokenString = jsonNode.get("refreshToken").asText();

        // Truy vấn DB kiểm tra sự tồn tại trong bảng refresh_tokens
        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElse(null);

        assertNotNull(savedToken, "Refresh Token phải được lưu vào bảng refresh_tokens trong DB");
        assertEquals("student", savedToken.getUser().getUsername(), "Token phải liên kết với user 'student'");

        // Kiểm tra thời hạn khoảng 7 ngày (7 * 24 * 60 * 60 giây)
        Instant expiryDate = savedToken.getExpiryDate();
        assertTrue(expiryDate.isAfter(Instant.now().plusSeconds(6 * 24 * 3600)),
                "Refresh Token phải có thời hạn xấp xỉ 7 ngày");
    }

    @Test
    @DisplayName("4. Đăng nhập thành công với tài khoản INSTRUCTOR -> Role trong token là INSTRUCTOR")
    void testLoginSuccessInstructor() throws Exception {
        LoginRequest loginRequest = new LoginRequest("instructor", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.get("accessToken").asText();

        Claims claims = jwtUtils.parseClaims(accessToken);
        assertEquals("instructor", claims.getSubject());
        assertEquals("INSTRUCTOR", claims.get("role"));
        assertNotNull(claims.getId());
    }

    @Test
    @DisplayName("5. Đăng nhập sai mật khẩu -> Hệ thống từ chối với mã lỗi 401 Unauthorized")
    void testLoginInvalidPasswordFails() throws Exception {
        LoginRequest loginRequest = new LoginRequest("student", "saiMatKhau123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu!"));
    }

    @Test
    @DisplayName("6. Gửi Refresh Token hợp lệ vào /refresh -> Nhận cặp Token mới (Token Rotation)")
    void testRefreshTokenRotationSuccess() throws Exception {
        // 1. Đăng nhập lấy token ban đầu
        LoginRequest loginRequest = new LoginRequest("student", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken1 = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // 2. Gửi Refresh Token vào /refresh
        String refreshPayload = String.format("{\"refreshToken\":\"%s\"}", refreshToken1);
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String refreshToken2 = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        assertNotEquals(refreshToken1, refreshToken2, "Refresh Token mới phải khác Refresh Token cũ");
    }

    @Test
    @DisplayName("7. Gửi lại chính Refresh Token cũ đã bị xoay vòng -> Hệ thống từ chối với mã 403 Forbidden")
    void testRefreshTokenRotationReuseFailsWith403() throws Exception {
        // 1. Đăng nhập lấy token
        LoginRequest loginRequest = new LoginRequest("student", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshTokenOld = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // 2. Xoay vòng lần 1 -> Thành công
        String refreshPayload = String.format("{\"refreshToken\":\"%s\"}", refreshTokenOld);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk());

        // 3. Gửi lại chính Refresh Token cũ đó một lần nữa -> Hệ thống từ chối (403)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Refresh token không tồn tại trong DB hoặc đã bị thu hồi/xoay vòng!")));
    }
}
