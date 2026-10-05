package com.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private Key key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Bỏ qua các endpoint công khai (ví dụ /api/auth/** của Identity-Service)
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 2. Với các request /api/courses/**: Kiểm tra Header Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            logger.warn(">>> [Gateway Chặn 401] Yêu cầu tới '{}' không đính kèm Access Token hoặc sai định dạng", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Thiếu hoặc sai định dạng Header Authorization Bearer Token!");
        }

        String token = authHeader.substring(7);

        // 3. Giải mã JWT bằng Secret Key dùng chung với Identity Service
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(key())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.warn(">>> [Gateway Chặn 401] Token đã hết hạn khi truy cập '{}': {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Access Token đã hết hạn! Vui lòng đăng nhập lại.");
        } catch (SignatureException e) {
            logger.warn(">>> [Gateway Chặn 401] Chữ ký Token không hợp lệ khi truy cập '{}': {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Access Token sai chữ ký số!");
        } catch (MalformedJwtException e) {
            logger.warn(">>> [Gateway Chặn 401] Token sai định dạng khi truy cập '{}': {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Access Token không đúng định dạng JWT!");
        } catch (Exception e) {
            logger.warn(">>> [Gateway Chặn 401] Lỗi xác thực Token khi truy cập '{}': {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Access Token không hợp lệ: " + e.getMessage());
        }

        // 4. Trích xuất role từ Payload
        String role = claims.get("role", String.class);
        if (!StringUtils.hasText(role)) {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?> list && !list.isEmpty()) {
                role = list.get(0).toString();
            } else {
                role = "STUDENT";
            }
        }

        String username = claims.getSubject();
        logger.info(">>> [Gateway Cho Phép] Token hợp lệ! User='{}', Role='{}' truy cập '{}'", username, role, path);

        // 5. Gắn role vào Header mới 'X-User-Role' trước khi forward request xuống Course-Service
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Role", role)
                .header("X-User-Name", username != null ? username : "")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":%d,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                status.value(), message, exchange.getRequest().getURI().getPath());

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Ưu tiên chạy sớm nhất trước các routing filter
    }
}
