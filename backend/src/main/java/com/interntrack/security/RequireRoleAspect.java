package com.interntrack.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Spring AOP Aspect intercepting calls to methods or classes decorated with @RequireRole.
 * Guarantees strict RBAC enforcement without duplicating inline checks across controllers.
 */
@Aspect
@Component
public class RequireRoleAspect {

    private static final Logger log = LoggerFactory.getLogger(RequireRoleAspect.class);

    @Before("@within(com.interntrack.security.RequireRole) || @annotation(com.interntrack.security.RequireRole)")
    public void verifyInstitutionalRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Inspect method-level annotation first, fall back to declaring type class annotation
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = joinPoint.getTarget().getClass().getAnnotation(RequireRole.class);
        }

        if (requireRole == null) {
            return;
        }

        String[] allowedRoles = requireRole.value();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("RBAC Rejection: unauthenticated attempt to execute {}", method.getName());
            throw new AccessDeniedException("You do not have permission to perform this action");
        }

        boolean hasRequiredRole = false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String userAuth = authority.getAuthority().toUpperCase();
            for (String role : allowedRoles) {
                String expectedRole = role.startsWith("ROLE_") ? role.toUpperCase() : "ROLE_" + role.toUpperCase();
                if (userAuth.equals(expectedRole) || userAuth.equals(role.toUpperCase())) {
                    hasRequiredRole = true;
                    break;
                }
            }
            if (hasRequiredRole) break;
        }

        if (!hasRequiredRole) {
            log.warn("RBAC Rejection: user [{}] with authorities {} attempted to access role-protected endpoint {}",
                    auth.getName(), auth.getAuthorities(), method.getName());
            // Do not leak any internal student or record detail in the rejection message
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }
}
