package com.interntrack.exception;

import com.interntrack.security.SecurityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception interceptor ensuring all API error responses follow direct,
 * informative college-portal terminology without generic AI-style placeholder text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired(required = false)
    private SecurityAuditLogger auditLogger;

    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<Map<String, Object>> handleCustomAuthException(CustomAuthException ex, HttpServletRequest request) {
        log.warn("Authentication rejected on path {}: {}", request.getRequestURI(), ex.getMessage());
        if (auditLogger != null) auditLogger.logAuthFailure(request.getRequestURI(), SecurityAuditLogger.extractClientIp(request), ex.getMessage());
        return buildErrorResponse("Authentication failed: valid college credentials required", ex.getMessage(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Security rejection on path {}: {}", request.getRequestURI(), ex.getMessage());
        if (auditLogger != null) auditLogger.logAuthFailure(request.getRequestURI(), SecurityAuditLogger.extractClientIp(request), ex.getMessage());
        return buildErrorResponse("Access rejected: active institutional session expired or unverified", ex.getMessage(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Unauthorized role attempt on path {}: {}", request.getRequestURI(), ex.getMessage());
        String actorUid = (request.getUserPrincipal() != null && request.getUserPrincipal().getName() != null) ? request.getUserPrincipal().getName() : "UNIDENTIFIED_PRINCIPAL";
        if (auditLogger != null) auditLogger.logRbacFailure(request.getRequestURI(), actorUid, "AUTHORIZED_ROLE_PERMS", request);
        return buildErrorResponse("Authorization failed: assigned institutional role lacks permission for this academic department record", ex.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Submission rejected: required parameters incomplete or invalid");
        body.put("details", fieldErrors);
        body.put("path", request.getRequestURI());
        
        log.warn("Validation error on path {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({InvalidRegistrationException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleRegistrationException(RuntimeException ex, HttpServletRequest request) {
        log.warn("Registration validation rejected on path {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(ex.getMessage(), ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Internal service error on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse("Service interruption: system processing failure encountered", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String title, String message, HttpStatus status, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", title);
        body.put("message", message != null ? message : "No technical exception details provided by host.");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
