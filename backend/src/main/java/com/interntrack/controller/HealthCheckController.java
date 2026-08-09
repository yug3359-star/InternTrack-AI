package com.interntrack.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Public diagnostic endpoints verifying system operation, active session authentication states,
 * and live cloud connectivity with Firebase Firestore and Authentication services.
 */
@RestController
@RequestMapping("/api")
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;

    public HealthCheckController(@Autowired(required = false) Firestore firestore,
                                 @Autowired(required = false) FirebaseAuth firebaseAuth) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> systemHealthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "alive");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("service", "InternTrack AI — Institutional Academic Portal Backend");
        status.put("operationalState", "ONLINE_HEALTHY");
        status.put("serverTimestamp", LocalDateTime.now().toString());
        status.put("databaseMode", firestore != null ? "CLOUD_FIRESTORE_PRODUCTION" : "H2_EMBEDDED_DEV_RUNTIME");
        status.put("securityFilter", "STATELESS_JWT_VERIFICATION_ENABLED");
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health/firebase")
    public ResponseEntity<Map<String, Object>> firebaseHealthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", LocalDateTime.now().toString());

        if (firestore == null) {
            status.put("status", "disconnected");
            status.put("message", "Firebase service account JSON not found or credentials unauthenticated in backend resources.");
            log.warn("[Health Check] GET /api/health/firebase checked: Firestore Bean is disconnected or uninitialized.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }

        try {
            // Perform trivial lightweight read against Firestore project to verify real cloud connectivity
            long collectionCount = 0;
            for (Object ignored : firestore.listCollections()) {
                collectionCount++;
            }
            status.put("status", "connected");
            status.put("service", "Firebase Firestore & Admin SDK");
            status.put("collectionsDetected", collectionCount);
            status.put("authBeanActive", firebaseAuth != null);
            log.info("[Health Check] GET /api/health/firebase verified live connection successfully. Detected [{}] collections.", collectionCount);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            status.put("status", "error");
            status.put("message", "Failed to interact with Firestore runtime: " + e.getMessage());
            log.error("[Health Check] GET /api/health/firebase encountered runtime communication error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(status);
        }
    }

    @GetMapping("/auth/status")
    public ResponseEntity<Map<String, Object>> getAuthenticationStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> session = new HashMap<>();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            session.put("authenticated", true);
            session.put("principalId", auth.getPrincipal());
            session.put("email", auth.getCredentials());
            session.put("grantedAuthorities", auth.getAuthorities());
        } else {
            session.put("authenticated", false);
            session.put("message", "No active institutional session token present in authorization header.");
        }
        return ResponseEntity.ok(session);
    }
}
