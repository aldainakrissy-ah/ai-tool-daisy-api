package com.example.ai.tool.analysis.ai_tool_daisy_api.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTH_HEADER = "Authorization";

    @Value("${api.key:}")
    private String apiKey;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
            throws IOException {
        try {
            String requestApiKey = extractApiKey(request);
            if (requestApiKey == null) {
                sendUnauthorizedResponse(response, "Missing API Key");
                return;
            }

            if (StringUtils.hasText(apiKey) && apiKey.equals(requestApiKey)) {
                authenticate(request);
                filterChain.doFilter(request, response);
            } else {
                logger.warn("Invalid API key attempt from IP: {}", request.getRemoteAddr());
                sendUnauthorizedResponse(response, "Invalid API Key");
            }
        } catch (Exception e) {
            logger.error("Error during API key authentication", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.getWriter().write("Authentication error");
        }
    }

    private String extractApiKey(HttpServletRequest request) {
        // Check X-API-Key header first
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(apiKeyHeader)) {
            return apiKeyHeader.trim();
        }

        // Fallback to Authorization header
        String authHeader = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }
        authHeader = authHeader.trim();
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader;
    }

    private void authenticate(HttpServletRequest request) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_API"));
        var authentication = new UsernamePasswordAuthenticationToken("api-client", null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
