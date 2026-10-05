package com.identity.security.services;

import com.identity.dto.response.TokenResponse;
import com.identity.exception.TokenRefreshException;
import com.identity.model.RefreshToken;
import com.identity.model.User;
import com.identity.repository.RefreshTokenRepository;
import com.identity.repository.UserRepository;
import com.identity.security.jwt.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${app.jwt.refresh-token-expiration}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Cơ chế Token Rotation:
     * 1. Tìm Refresh Token trong DB. Nếu không tồn tại -> ném TokenRefreshException (403 Forbidden).
     * 2. Nếu đã hết hạn -> ném TokenRefreshException (403 Forbidden).
     * 3. Xoay vòng (Rotation): thay thế token cũ bằng token ngẫu nhiên mới trong DB.
     * 4. Sinh Access Token mới và trả về cặp Token mới cho Client.
     */
    @Transactional
    public TokenResponse refreshTokenRotation(String requestToken) {
        RefreshToken tokenRecord = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new TokenRefreshException(requestToken,
                        "Refresh token không tồn tại trong DB hoặc đã bị thu hồi/xoay vòng!"));

        if (tokenRecord.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(tokenRecord);
            throw new TokenRefreshException(requestToken, "Refresh token đã hết hạn! Vui lòng đăng nhập lại.");
        }

        User user = tokenRecord.getUser();

        // 3. Thực hiện Token Rotation: Xóa/thay thế giá trị token cũ trong DB
        String newRefreshTokenString = UUID.randomUUID().toString();
        tokenRecord.setToken(newRefreshTokenString);
        tokenRecord.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshTokenRepository.save(tokenRecord);

        // 4. Sinh Access Token mới
        String newAccessToken = jwtUtils.generateAccessToken(user);

        return new TokenResponse(
                newAccessToken,
                newRefreshTokenString,
                900L,
                user.getUsername(),
                user.getPrimaryRole()
        );
    }

    /**
     * Sinh một Refresh Token (chuỗi ngẫu nhiên UUID, sống 7 ngày) và lưu vào bảng refresh_tokens trong DB.
     *
     * @param userId ID của người dùng
     * @return Đối tượng RefreshToken đã được lưu vào cơ sở dữ liệu
     */
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        // Kiểm tra xem người dùng đã có Refresh Token chưa. Nếu có, cập nhật; nếu chưa, tạo mới
        RefreshToken refreshToken = refreshTokenRepository.findByUser(user)
                .orElse(new RefreshToken());

        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Kiểm tra tính hợp lệ về thời gian hết hạn của Refresh Token.
     */
    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token đã hết hạn! Vui lòng đăng nhập lại.");
        }
        return token;
    }

    @Transactional
    public int deleteByUserId(Long userId) {
        return userRepository.findById(userId)
                .map(user -> refreshTokenRepository.deleteByUser(user))
                .orElse(0);
    }
}
