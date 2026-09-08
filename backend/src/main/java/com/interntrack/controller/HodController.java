package com.interntrack.controller;

import com.interntrack.dto.SecurityValidationDtos;
import com.interntrack.security.RequireRole;
import com.interntrack.security.SecurityAuditLogger;
import com.interntrack.service.HodService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Thin REST controller exposing departmental HOD application evaluation and status-filtered ledgers.
 * Protected universally by reusable @RequireRole("hod") security interception.
 */
@RestController
@RequestMapping("/api/hod/applications")
@RequireRole("hod")
public class HodController {

    private final HodService hodService;
    private final SecurityAuditLogger auditLogger;

    public HodController(HodService hodService, SecurityAuditLogger auditLogger) {
        this.hodService = hodService;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPendingApplications(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "status", required = false, defaultValue = "ALL") String status) {
        return ResponseEntity.ok(hodService.getPendingApplications(page, status));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> getApplicationDetail(@PathVariable String uid) {
        return ResponseEntity.ok(hodService.getApplicationDetail(uid));
    }

    @PatchMapping("/{uid}/approve")
    public ResponseEntity<Map<String, Object>> approveApplication(
            @PathVariable String uid,
            @Valid @RequestBody SecurityValidationDtos.ApproveApplicationDto payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        Map<String, Object> result = hodService.approveApplication(uid, hodUid, payload.getCollegeMentor());
        auditLogger.logAdminAction("HOD_APPLICATION_APPROVAL", hodUid, uid, "Transitioned student application to ONGOING status after institutional credentials evaluation");
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{uid}/reject")
    public ResponseEntity<Map<String, Object>> rejectApplication(
            @PathVariable String uid,
            @Valid @RequestBody(required = false) SecurityValidationDtos.RejectReasonDto payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        String reason = (payload != null && payload.getReason() != null) ? payload.getReason() : null;
        Map<String, Object> result = hodService.rejectApplication(uid, reason, hodUid);
        auditLogger.logAdminAction("HOD_APPLICATION_REJECTION", hodUid, uid, "Rejected candidate onboarding application. Reason: " + (reason != null ? reason : "Unspecified compliance failure"));
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createApplication(
            @Valid @RequestBody SecurityValidationDtos.ApplicationCrudDto payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        Map<String, Object> result = hodService.createApplication(payload, hodUid);
        auditLogger.logAdminAction("HOD_APPLICATION_CREATION", hodUid, (String) result.get("uid"), "Manually created student application");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> updateApplication(
            @PathVariable String uid,
            @Valid @RequestBody SecurityValidationDtos.ApplicationCrudDto payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        Map<String, Object> result = hodService.updateApplication(uid, payload, hodUid);
        auditLogger.logAdminAction("HOD_APPLICATION_UPDATE", hodUid, uid, "Manually updated student application");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> deleteApplication(
            @PathVariable String uid) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        Map<String, Object> result = hodService.deleteApplication(uid, hodUid);
        auditLogger.logAdminAction("HOD_APPLICATION_DELETION", hodUid, uid, "Manually deleted student application");
        return ResponseEntity.ok(result);
    }
}
