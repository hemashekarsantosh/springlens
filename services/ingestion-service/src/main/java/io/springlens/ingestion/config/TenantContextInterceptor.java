package io.springlens.ingestion.config;

import io.springlens.shared.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

/**
 * Sets app.current_workspace_id PostgreSQL session variable for Row-Level Security.
 * Reads workspaceId from request attribute set by ApiKeyAuthFilter.
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantContextInterceptor.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Object workspaceIdAttr = request.getAttribute("workspaceId");
        if (workspaceIdAttr instanceof UUID workspaceId) {
            TenantContext.setWorkspaceId(workspaceId);
            try {
                entityManager.createNativeQuery(
                        "SET app.current_workspace_id = '" + workspaceId + "'"
                ).executeUpdate();
            } catch (Exception ex) {
                log.warn("Failed to set RLS workspace_id={} error={}", workspaceId, ex.getMessage());
            }
        }

        // Set X-Request-ID response header
        String requestId = (String) request.getAttribute("X-Request-ID");
        if (requestId != null) {
            response.setHeader("X-Request-ID", requestId);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
