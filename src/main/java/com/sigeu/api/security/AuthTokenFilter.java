package com.sigeu.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class AuthTokenFilter extends HttpFilter {
    public static final String AUTH_USER_ATTRIBUTE = "sigeu.auth.user";

    private final JwtService jwtService;
    private final boolean requireToken;

    public AuthTokenFilter(
            JwtService jwtService,
            @Value("${sigeu.auth.require-token:false}") boolean requireToken
    ) {
        this.jwtService = jwtService;
        this.requireToken = requireToken;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Optional<JwtService.AuthenticatedUser> authenticatedUser = jwtService.validateToken(extractBearerToken(request));
        authenticatedUser.ifPresent(user -> request.setAttribute(AUTH_USER_ATTRIBUTE, user));

        if (requireToken && isProtectedApiRequest(request) && authenticatedUser.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Token requerido o invalido");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isProtectedApiRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return false;

        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) return false;
        return !path.startsWith("/api/auth/");
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring("Bearer ".length()).trim();
    }
}
