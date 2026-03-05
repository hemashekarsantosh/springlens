package io.springlens.ingestion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validates sl_proj_* bearer tokens by calling auth-service /internal/validate-key.
 * On success, sets workspaceId and projectId as request attributes.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient authClient;

    public ApiKeyAuthFilter(
            @Value("${springlens.auth-service.url:http://localhost:8084}") String authServiceUrl) {
        this.authClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip non-agent endpoints
        String path = request.getRequestURI();
        if (path.equals("/v1/healthz") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!apiKey.startsWith("sl_proj_")) {
            sendUnauthorized(response, "Invalid API key format");
            return;
        }

        try {
            var validationResult = authClient.get()
                    .uri("/internal/validate-key?key=" + apiKey)
                    .retrieve()
                    .body(ApiKeyValidationResult.class);

            if (validationResult == null || validationResult.workspaceId() == null) {
                sendUnauthorized(response, "Invalid or revoked API key");
                return;
            }

            // Set tenant context as request attributes
            request.setAttribute("workspaceId", validationResult.workspaceId());
            request.setAttribute("projectId", validationResult.projectId());
            request.setAttribute("X-Request-ID", request.getHeader("X-Request-ID") != null
                    ? request.getHeader("X-Request-ID")
                    : UUID.randomUUID().toString());

            // Set Spring Security context
            var auth = new UsernamePasswordAuthenticationToken(
                    validationResult.workspaceId().toString(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("API key authenticated workspace={} project={}", validationResult.workspaceId(), validationResult.projectId());
            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            log.warn("API key validation failed key_prefix={} error={}", apiKey.substring(0, Math.min(12, apiKey.length())), ex.getMessage());
            sendUnauthorized(response, "API key validation failed");
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("""
                {"code":"UNAUTHORIZED","message":"%s","trace_id":null}
                """.formatted(message));
    }

    public record ApiKeyValidationResult(UUID workspaceId, UUID projectId) {
    }
}
