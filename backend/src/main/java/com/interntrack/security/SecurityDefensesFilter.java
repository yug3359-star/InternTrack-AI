package com.interntrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Enterprise security defensive filter responsible for:
 * 1. HTTPS enforcement in deployed production environments (rejecting plain text interception).
 * 2. HTTP response security headers (CSP, HSTS, X-Content-Type-Options, Referrer-Policy, X-Frame-Options).
 * 3. Bucket4j IP rate-limiting enforcement on sensitive authentication and profile registration endpoints.
 */
@Component
public class SecurityDefensesFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityDefensesFilter.class);

    private final RateLimitService rateLimitService;
    private final SecurityAuditLogger auditLogger;

    public SecurityDefensesFilter(RateLimitService rateLimitService, SecurityAuditLogger auditLogger) {
        this.rateLimitService = rateLimitService;
        this.auditLogger = auditLogger;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String clientIp = SecurityAuditLogger.extractClientIp(request);

        // 1. HTTP Security Headers Enforcement (Task 4)
        // Prevents Clickjacking, MIME type sniffing drive-by exploits, and Cross-Site Scripting (XSS)
        response.setHeader("Content-Security-Policy", "default-src 'self' https: 'unsafe-inline' 'unsafe-eval'; frame-ancestors 'self'; object-src 'none';");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (uri.startsWith("/h2-console")) {
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
        } else {
            response.setHeader("X-Frame-Options", "DENY");
        }
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(self \"https://interntrack-ai-98f45.web.app\" \"http://localhost:5173\" \"http://localhost:5174\"), microphone=(), geolocation=()");

        // 2. HTTPS Enforcement in Production (Task 1)
        // If accessed via remote hostname (not localhost / 127.0.0.1) and protocol is plain HTTP, reject immediately
        boolean isLocalHost = "localhost".equalsIgnoreCase(request.getServerName()) || "127.0.0.1".equals(request.getServerName()) || "0:0:0:0:0:0:0:1".equals(request.getServerName());
        String protoHeader = request.getHeader("X-Forwarded-Proto");
        boolean isHttpProto = "http".equalsIgnoreCase(protoHeader) || (!request.isSecure() && protoHeader == null);

        if (!isLocalHost && isHttpProto && !uri.startsWith("/api/health")) {
            log.warn("Rejected unencrypted HTTP request to [{}] from remote origin [{}]. HTTPS encryption required.", uri, clientIp);
            auditLogger.logAuthFailure(uri, clientIp, "Rejected non-HTTPS connection attempt in deployed institutional environment.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Insecure Transport Rejected\", \"message\": \"Strict HTTPS encryption is mandatory in production to prevent TLS session eavesdropping and JWT credential sniffing.\", \"status\": 403, \"timestamp\": \"" + LocalDateTime.now() + "\"}");
            return;
        }

        // 3. Rate Limiting via Bucket4j on sensitive endpoints (Task 2)
        // Prevents automated credential stuffing and brute-force registration flood attacks
        if (isSensitiveAuthEndpoint(uri) && !request.getMethod().equalsIgnoreCase("OPTIONS")) {
            String rateKey = "AUTH_THROTTLE_" + clientIp;
            if (!rateLimitService.tryConsumeAuthToken(rateKey)) {
                auditLogger.logRateLimitViolation(uri, clientIp);
                response.setStatus(429); // HTTP 429 Too Many Requests
                response.setContentType("application/json");
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"error\": \"Rate Limit Exceeded\", \"message\": \"Too many authentication or onboarding attempts from this IP address (max 5 per minute). Please wait 60 seconds before retrying to ensure platform DDoS safety.\", \"status\": 429, \"timestamp\": \"" + LocalDateTime.now() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSensitiveAuthEndpoint(String uri) {
        return uri.startsWith("/api/auth/") || 
               uri.contains("/register") || 
               uri.contains("/login") ||
               uri.contains("/promote-hod");
    }
}
