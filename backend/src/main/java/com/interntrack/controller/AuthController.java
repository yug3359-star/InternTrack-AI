package com.interntrack.controller;

import com.interntrack.dto.RegisterRequest;
import com.interntrack.dto.SecurityValidationDtos;
import com.interntrack.security.SecurityAuditLogger;
import com.interntrack.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityAuditLogger auditLogger;

    public AuthController(AuthService authService, SecurityAuditLogger auditLogger) {
        this.authService = authService;
        this.auditLogger = auditLogger;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> register(@Valid @ModelAttribute RegisterRequest request) {
        Map<String, Object> result = authService.register(request);
        auditLogger.logAdminAction("STUDENT_REGISTRATION_ACCEPTED", request.getCollegeEmail(), request.getBranch(), "Onboarding documents and baseline biometric reference enrolled successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/promote-hod")
    public ResponseEntity<Map<String, Object>> promoteHod(@Valid @RequestBody SecurityValidationDtos.PromoteHodDto request) {
        String targetEmail = request.getTargetEmail();
        String setupSecret = request.getSetupSecret();
        Map<String, Object> result = authService.promoteToHod(targetEmail, setupSecret);
        auditLogger.logAdminAction("BREAK_GLASS_HOD_PROMOTION", "EMERGENCY_SETUP_SECRET", targetEmail, "Promoted account to HOD administrative status via verified secret challenge");
        return ResponseEntity.ok(result);
    }
}
