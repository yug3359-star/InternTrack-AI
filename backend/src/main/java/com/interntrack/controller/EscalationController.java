package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.EscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin REST controller exposing Department Head (HOD) monitoring endpoints for student escalation
 * and on-demand manual cron triggers for monthly warning evaluations.
 */
@RestController
@RequestMapping("/api")
public class EscalationController {

    private static final Logger log = LoggerFactory.getLogger(EscalationController.class);

    private final EscalationService escalationService;

    public EscalationController(EscalationService escalationService) {
        this.escalationService = escalationService;
    }

    /**
     * Retrieves current real list of highlighted students for the active monthly audit period.
     * Universal HOD role enforcement via @RequireRole.
     */
    @GetMapping("/hod/highlighted")
    @RequireRole("hod")
    public ResponseEntity<List<Map<String, Object>>> getHighlightedStudents() {
        return ResponseEntity.ok(escalationService.getHighlightedStudents());
    }

    /**
     * Retrieves month-by-month historical warning ledgers for an individual candidate over time.
     */
    @GetMapping("/hod/highlighted/{uid}/history")
    @RequireRole("hod")
    public ResponseEntity<List<Map<String, Object>>> getStudentWarningHistory(@PathVariable String uid) {
        return ResponseEntity.ok(escalationService.getStudentWarningHistory(uid));
    }

    /**
     * Retrieves total count of active highlighted students for dynamic navigation notification badges.
     */
    @GetMapping("/hod/highlighted/count")
    @RequireRole("hod")
    public ResponseEntity<Map<String, Object>> getHighlightedCount() {
        return ResponseEntity.ok(escalationService.getHighlightedCount());
    }

    /**
     * On-demand manual execution of the monthly escalation and highlighting evaluation audit.
     * Allows HODs to verify and flag non-compliant records immediately without waiting for scheduled monthly cron cycles.
     */
    @PostMapping("/admin/run-escalation-job")
    @RequireRole("hod")
    public ResponseEntity<Map<String, Object>> runEscalationJobManually(
            @RequestParam(name = "month", required = false) String targetMonth) {
        log.info("On-demand escalation evaluation audit triggered manually by HOD administration account for month: [{}]", 
                targetMonth != null ? targetMonth : "CURRENT_ACTIVE_MONTH");
        
        int flaggedCount = escalationService.runEscalationCheck(targetMonth);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Escalation audit completed successfully.");
        response.put("flaggedCount", flaggedCount);
        response.put("timestamp", ZonedDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
