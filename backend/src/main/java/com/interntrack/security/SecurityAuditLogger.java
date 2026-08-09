package com.interntrack.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dedicated security audit logger providing an immutable audit trail of authentication failures,
 * RBAC permission rejections (403), brute-force attempts, and sensitive administrative HOD interventions.
 * Prevents non-repudiation vulnerabilities and fulfills institutional GDPR/DPDP campus audit compliance.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT_LEDGER");
    private static final Marker AUDIT_MARKER = MarkerFactory.getMarker("SECURITY_AUDIT");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

    public void logRbacFailure(String uri, String actorUid, String requiredPermission, HttpServletRequest request) {
        String ip = extractClientIp(request);
        auditLog.warn(AUDIT_MARKER, "[SECURITY_AUDIT] timestamp={} | event=RBAC_ACCESS_DENIED | actorUid={} | clientIp={} | targetUri={} | requiredRole={} | details=Unauthorized access attempt blocked by institutional security filter",
                currentTimestamp(), actorUid, ip, uri, requiredPermission);
    }

    public void logAuthFailure(String uri, String ipAddress, String reason) {
        auditLog.warn(AUDIT_MARKER, "[SECURITY_AUDIT] timestamp={} | event=AUTHENTICATION_REJECTED | actorUid=ANONYMOUS | clientIp={} | targetUri={} | details={}",
                currentTimestamp(), ipAddress, uri, reason);
    }

    public void logAdminAction(String action, String actorUid, String targetRecord, String details) {
        auditLog.info(AUDIT_MARKER, "[SECURITY_AUDIT] timestamp={} | event=ADMIN_HOD_ACTION | action={} | actorUid={} | targetRecord={} | details={}",
                currentTimestamp(), action, actorUid, targetRecord, details);
    }

    public void logRateLimitViolation(String uri, String ipAddress) {
        auditLog.warn(AUDIT_MARKER, "[SECURITY_AUDIT] timestamp={} | event=RATE_LIMIT_VIOLATION_HTTP_429 | actorUid=UNAUTHENTICATED | clientIp={} | targetUri={} | details=Brute-force credential stuffing or spam attack mitigated by Bucket4j throttle",
                currentTimestamp(), ipAddress, uri);
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN_IP";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN_IP";
    }

    private static String currentTimestamp() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(ISO_FORMAT);
    }
}
