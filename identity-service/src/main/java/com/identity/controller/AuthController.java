package com.identity.controller;

import com.identity.dto.request.LoginRequest;
import com.identity.dto.request.TokenRefreshRequest;
import com.identity.dto.response.MessageResponse;
import com.identity.dto.response.TokenResponse;
import com.identity.model.RefreshToken;
import com.identity.model.User;
import com.identity.security.jwt.JwtUtils;
import com.identity.security.services.RefreshTokenService;
import com.identity.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * API POST /api/auth/login
     * Luồng xử lý:
     * 1. So sánh mật khẩu gửi lên với mật khẩu đã băm (BCrypt) trong DB.
     * 2. Nếu đúng, sử dụng JJWT sinh ra Access Token (thuật toán HS256) sống trong 15 phút.
     *    Payload chứa: Standard claim sub (username), Private claim role (ví dụ: STUDENT, INSTRUCTOR),
     *    bắt buộc có claim jti (JWT ID - mã định danh duy nhất của token).
     * 3. Sinh thêm một Refresh Token (chuỗi ngẫu nhiên UUID, sống 7 ngày) và lưu thông tin vào bảng refresh_tokens trong DB.
     * 4. Trả về client cả cặp Access và Refresh Token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        // 1. So sánh mật khẩu gửi lên với mật khẩu đã băm (BCrypt) trong DB
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // 2. JJWT sinh Access Token (HS256, 15 phút, sub, role, jti)
        String accessToken = jwtUtils.generateAccessToken(userDetails);

        // 3. Sinh Refresh Token (chuỗi ngẫu nhiên, hạn 7 ngày) và lưu DB
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        // 4. Trả về client cả cặp Access và Refresh Token
        return ResponseEntity.ok(new TokenResponse(
                accessToken,
                refreshToken.getToken(),
                900L, // 15 phút = 900 giây
                userDetails.getUsername(),
                userDetails.getRole()
        ));
    }

    /**
     * API POST /api/auth/refresh
     * Cấp mới Access Token và Refresh Token từ Refresh Token hợp lệ (Cơ chế Token Rotation)
     * Nếu token không hợp lệ hoặc đã bị xoay vòng -> ném TokenRefreshException (403 Forbidden)
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        TokenResponse response = refreshTokenService.refreshTokenRotation(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}
