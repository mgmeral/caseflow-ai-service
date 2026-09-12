package com.caseflow.ai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Optional internal service-to-service authentication.
 * Only active when {@code caseflow.ai.auth.enabled=true}.
 *
 * <p>When enabled, all requests to {@code /api/**} must include the header:
 * {@code X-Internal-Api-Key: <value of INTERNAL_API_KEY env var>}
 *
 * <p>This is a simple bearer-token style check suitable for internal network-boundary enforcement.
 * For higher-assurance environments, replace with mTLS or JWT verification.
 */
@Configuration
@ConditionalOnProperty(name = "caseflow.ai.auth.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InternalAuthConfig {

    private final AppConfig appConfig;

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter() {
        FilterRegistrationBean<InternalApiKeyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiKeyFilter(appConfig.getAuth().getInternalApiKey()));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        bean.setName("internalApiKeyFilter");
        log.info("InternalApiKeyFilter registered for /api/*");
        return bean;
    }

    static class InternalApiKeyFilter extends OncePerRequestFilter {

        private static final String HEADER_NAME = "X-Internal-Api-Key";
        private final String expectedKey;

        InternalApiKeyFilter(String expectedKey) {
            this.expectedKey = expectedKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {

            // Pass-through for actuator endpoints
            String path = request.getRequestURI();
            if (path.startsWith("/actuator") || path.startsWith("/api-docs") || path.startsWith("/swagger-ui")) {
                filterChain.doFilter(request, response);
                return;
            }

            String providedKey = request.getHeader(HEADER_NAME);
            if (expectedKey == null || expectedKey.isBlank()) {
                // Misconfigured — auth is enabled but key is not set; reject all requests
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Internal auth misconfiguration\"}");
                return;
            }

            if (!expectedKey.equals(providedKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Missing or invalid X-Internal-Api-Key\"}");
                return;
            }

            filterChain.doFilter(request, response);
        }
    }
}
