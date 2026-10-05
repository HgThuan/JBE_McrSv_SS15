package com.course.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Filter đọc trực tiếp Header X-User-Role và X-User-Name do Gateway truyền xuống.
 * Không cần giải mã lại JWT, ánh xạ thành GrantedAuthority cho Spring Security.
 */
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userRole = request.getHeader("X-User-Role");
        String userName = request.getHeader("X-User-Name");

        if (StringUtils.hasText(userRole)) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            // Authority chính xác theo yêu cầu đề bài: STUDENT, INSTRUCTOR
            authorities.add(new SimpleGrantedAuthority(userRole.trim()));
            if (!userRole.startsWith("ROLE_")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + userRole.trim()));
            }

            String principal = StringUtils.hasText(userName) ? userName.trim() : "user";

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.debug(">>> [Course-Service] Đã xác thực người dùng '{}' với quyền: {}", principal, authorities);
        }

        filterChain.doFilter(request, response);
    }
}
