package com.gateway;

import com.gateway.filter.JwtAuthenticationGlobalFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GatewayServiceApplicationTests {

    @Autowired
    private JwtAuthenticationGlobalFilter jwtFilter;

    private static final String SECRET = "vS6K5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2z";
    private Key key;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtFilter, "jwtSecret", SECRET);
    }

    private String createToken(String username, String role, long durationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + durationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("jti", UUID.randomUUID().toString());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setId((String) claims.get("jti"))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("1. Yêu cầu /api/courses không có Token -> Chặn ngay 401 Unauthorized")
    void testRequestWithoutToken_BlockedWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        jwtFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("2. Yêu cầu /api/courses với Token đã hết hạn -> Chặn ngay 401 Unauthorized")
    void testRequestWithExpiredToken_BlockedWith401() {
        // Token đã hết hạn 10 phút trước (-600.000 ms)
        String expiredToken = createToken("student", "STUDENT", -600000L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        jwtFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("3. Yêu cầu /api/courses với Token sai chữ ký -> Chặn ngay 401 Unauthorized")
    void testRequestWithInvalidSignature_BlockedWith401() {
        // Ký bằng Secret Key hoàn toàn khác
        Key wrongKey = Keys.hmacShaKeyFor("wrongSecretKeyForTestingInvalidSignature123456789".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .setSubject("hacker")
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        jwtFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("4. Yêu cầu với Token STUDENT hợp lệ -> Forward và gắn Header X-User-Role: STUDENT")
    void testValidToken_AttachesXUserRoleStudent() {
        String validToken = createToken("student", "STUDENT", 900000L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> forwardedRole = new AtomicReference<>();
        AtomicReference<String> forwardedUser = new AtomicReference<>();

        GatewayFilterChain chain = filterExchange -> {
            forwardedRole.set(filterExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            forwardedUser.set(filterExchange.getRequest().getHeaders().getFirst("X-User-Name"));
            return Mono.empty();
        };

        jwtFilter.filter(exchange, chain).block();

        assertEquals("STUDENT", forwardedRole.get(), "Gateway phải gắn Header X-User-Role: STUDENT");
        assertEquals("student", forwardedUser.get(), "Gateway phải gắn Header X-User-Name: student");
    }

    @Test
    @DisplayName("5. Yêu cầu với Token INSTRUCTOR hợp lệ -> Forward và gắn Header X-User-Role: INSTRUCTOR")
    void testValidToken_AttachesXUserRoleInstructor() {
        String validToken = createToken("instructor", "INSTRUCTOR", 900000L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> forwardedRole = new AtomicReference<>();

        GatewayFilterChain chain = filterExchange -> {
            forwardedRole.set(filterExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        };

        jwtFilter.filter(exchange, chain).block();

        assertEquals("INSTRUCTOR", forwardedRole.get(), "Gateway phải gắn Header X-User-Role: INSTRUCTOR");
    }
}
