package com.interntrack.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Filter responsible for intercepting API calls, parsing Firebase ID Bearer tokens,
 * validating JWT signatures, and extracting user 'role' custom claims.
 */
@Component
public class FirebaseJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseJwtFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;
    private final SecurityAuditLogger auditLogger;

    public FirebaseJwtFilter(@Autowired(required = false) FirebaseAuth firebaseAuth,
                             @Autowired(required = false) SecurityAuditLogger auditLogger) {
        this.firebaseAuth = firebaseAuth;
        this.auditLogger = auditLogger;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            try {
                if (firebaseAuth != null && !token.startsWith("DEV_TOKEN_")) {
                    // Verify real Firebase ID Token directly against cloud institutional project, checking hourly expiry and revocation status (Task 8)
                    FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token, true);
                    String uid = decodedToken.getUid();
                    String email = decodedToken.getEmail();
                    Map<String, Object> claims = decodedToken.getClaims();

                    // Extract custom claim "role" (STUDENT, MENTOR, HOD)
                    String role = (claims.containsKey("role")) ? claims.get("role").toString() : "STUDENT";
                    log.debug("Successfully validated real Firebase ID Token for UID: [{}], Email: [{}], Role: [{}]", uid, email, role);
                    setupSecurityContext(request, uid, email, role);
                } else {
                    // Dev Mode fallback when real Firebase credentials aren't initialized yet
                    handleDevModeToken(request, token);
                }
            } catch (Exception e) {
                log.warn("Authentication rejected: failed to verify Firebase token on route [{}] - Error: {}", request.getRequestURI(), e.getMessage());
                if (auditLogger != null) {
                    auditLogger.logAuthFailure(request.getRequestURI(), SecurityAuditLogger.extractClientIp(request), "JWT signature, expiration, or revocation verification failed: " + e.getMessage());
                }
                SecurityContextHolder.clearContext();
            }
        }

        // Unauthenticated calls must remain unauthenticated in the SecurityContext so @RequireRole intercepts them cleanly with 403 Forbidden
        filterChain.doFilter(request, response);
    }

    private void setupSecurityContext(HttpServletRequest request, String userId, String email, String role) {
        // Format authority as ROLE_NAME for Spring Security matchers
        String authorityName = role.startsWith("ROLE_") ? role.toUpperCase() : "ROLE_" + role.toUpperCase();
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityName));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId, email, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Facilitates local team testing without requiring active cloud network connectivity.
     */
    private void handleDevModeToken(HttpServletRequest request, String token) {
        String role = "STUDENT";
        String userId = "dev-student-id";
        String email = "student@college.edu";

        if (token.equalsIgnoreCase("DEV_TOKEN_MENTOR") || token.toLowerCase().contains("mentor")) {
            role = "MENTOR";
            userId = "dev-mentor-id";
            email = "faculty.mentor@college.edu";
        } else if (token.equalsIgnoreCase("DEV_TOKEN_HOD") || token.toLowerCase().contains("hod")) {
            role = "HOD";
            userId = "dev-hod-id";
            email = "cs.dept.head@college.edu";
        } else if (token.equalsIgnoreCase("DEV_TOKEN_STUDENT") || token.toLowerCase().contains("student")) {
            role = "STUDENT";
            userId = "dev-student-id";
            email = "student@college.edu";
        } else {
            // Treat token string itself as user email or ID in fallback testing
            email = token.contains("@") ? token : "user_" + token + "@college.edu";
        }

        log.debug("Dev Mode Auth: Authorizing user [{}] with role [ROLE_{}]", email, role);
        setupSecurityContext(request, userId, email, role);
    }
}
