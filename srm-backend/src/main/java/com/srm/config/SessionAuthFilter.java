package com.srm.config;

import com.srm.foundation.web.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 要求已登录会话（{@link AuthController#SESSION_USER_ID}）后访问业务 API。
 * 与方案开发计划 **B1-6** 一致；登录、公开注册、Swagger、OPTIONS 预检等除外。
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (isPublicPath(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(AuthController.SESSION_USER_ID) == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"未登录或会话已过期，请重新登录\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isPublicPath(String uri) {
        if (uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui") || "/swagger-ui.html".equals(uri)) {
            return true;
        }
        if (uri.startsWith("/actuator")) {
            return true;
        }
        if (uri.startsWith("/api/v1/public/")) {
            return true;
        }
        if (uri.startsWith("/api/v1/auth/login")) {
            return true;
        }
        if (uri.startsWith("/api/v1/auth/logout")) {
            return true;
        }
        return false;
    }
}
