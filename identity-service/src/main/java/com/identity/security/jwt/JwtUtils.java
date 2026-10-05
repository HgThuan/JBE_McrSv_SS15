package com.identity.security.jwt;

import com.identity.model.User;
import com.identity.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration}")
    private int jwtExpirationMs;

    private Key key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sinh Access Token từ UserDetailsImpl
     * - Thuật toán: HS256
     * - Thời gian sống: 15 phút
     * - Payload chứa:
     *     + Standard Claim: sub (username)
     *     + Private Claim: role (ví dụ: STUDENT, INSTRUCTOR)
     *     + Bắt buộc: jti (JWT ID - mã định danh duy nhất của token)
     */
    public String generateAccessToken(UserDetailsImpl userPrincipal) {
        String role = userPrincipal.getRole();
        return buildJwtToken(userPrincipal.getUsername(), role, userPrincipal.getId(), userPrincipal.getEmail());
    }

    /**
     * Sinh Access Token từ User entity
     */
    public String generateAccessToken(User user) {
        String role = user.getPrimaryRole();
        return buildJwtToken(user.getUsername(), role, user.getId(), user.getEmail());
    }

    /**
     * Helper xây dựng JWT theo chuẩn HS256 với sub, role, jti
     */
    private String buildJwtToken(String username, String role, Long userId, String email) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("jti", jti);
        if (userId != null) {
            claims.put("id", userId);
        }
        if (email != null) {
            claims.put("email", email);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setId(jti)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromJwtToken(String token) {
        return (String) parseClaims(token).get("role");
    }

    public String getJtiFromJwtToken(String token) {
        Claims claims = parseClaims(token);
        String jti = claims.getId();
        if (jti == null) {
            jti = (String) claims.get("jti");
        }
        return jti;
    }

    public Date getExpirationFromJwtToken(String token) {
        return parseClaims(token).getExpiration();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Token JWT không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string rỗng: {}", e.getMessage());
        }
        return false;
    }
}
