package com.interntrack.controller;

import com.interntrack.model.InternshipLog;
import com.interntrack.service.ComplianceMonitoringService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Role-protected institutional dashboard endpoints serving Student compliance metrics,
 * Mentor verification review queues, and Head of Department program statistics.
 */
@RestController
@RequestMapping("/api")
public class PortalController {

    private final ComplianceMonitoringService complianceService;
    private final com.interntrack.service.HodService hodService;

    public PortalController(ComplianceMonitoringService complianceService, com.interntrack.service.HodService hodService) {
        this.complianceService = complianceService;
        this.hodService = hodService;
    }

    // --- STUDENT ENDPOINTS (Requires ROLE_STUDENT or ROLE_HOD) ---

    @GetMapping("/student/compliance")
    public ResponseEntity<Map<String, Object>> getStudentCompliance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String studentId = auth != null ? auth.getPrincipal().toString() : "dev-student-001";
        return ResponseEntity.ok(complianceService.getStudentComplianceSummary(studentId));
    }

    @PostMapping("/student/logs")
    public ResponseEntity<InternshipLog> submitWeeklyLog(@Valid @RequestBody InternshipLog logEntry) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && logEntry.getStudentId() == null) {
            logEntry.setStudentId(auth.getPrincipal().toString());
        }
        return ResponseEntity.ok(complianceService.submitLog(logEntry));
    }

    // --- MENTOR ENDPOINTS (Requires ROLE_MENTOR or ROLE_HOD) ---

    @GetMapping("/mentor/students")
    public ResponseEntity<List<Map<String, Object>>> getMentorStudents(@RequestParam(name = "mentorName", defaultValue = "Dr. Rajesh K. (CS Dept)") String mentorName) {
        return ResponseEntity.ok(hodService.getMentorStudents(mentorName));
    }

    @GetMapping("/mentor/review-queue")
    public ResponseEntity<Map<String, Object>> getMentorReviewQueue() {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "departmentSection", "Computer Science - Cohort 2026",
                "pendingCount", 0,
                "notice", "No pending internship weekly logs requiring immediate verification."
        ));
    }

    // --- HOD ENDPOINTS (Requires ROLE_HOD solely) ---

    @GetMapping("/hod/analytics")
    public ResponseEntity<Map<String, Object>> getHodDepartmentAnalytics() {
        return ResponseEntity.ok(complianceService.getDepartmentComplianceStats());
    }
}
