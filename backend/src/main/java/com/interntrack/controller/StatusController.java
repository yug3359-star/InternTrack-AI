package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.CompletionService;
import com.interntrack.service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin REST controller exposing student status tracking endpoints and on-demand HOD status/completion cron execution.
 */
@RestController
@RequestMapping("/api")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    private final StatusService statusService;
    private final CompletionService completionService;

    public StatusController(StatusService statusService, CompletionService completionService) {
        this.statusService = statusService;
        this.completionService = completionService;
    }

    /**
     * Retrieves current lifecycle status and relevant transition timestamps for an individual candidate.
     * Accessible by students, mentors, and HODs.
     */
    @GetMapping("/status/{uid}")
    public ResponseEntity<Map<String, Object>> getStudentStatus(@PathVariable String uid) {
        return ResponseEntity.ok(statusService.getStudentStatus(uid));
    }

    /**
     * On-demand administrative status & completion transition evaluation.
     * Real production capability enabling HODs to verify and transition records immediately without waiting for hourly cron cycles.
     */
    @PostMapping("/admin/run-status-job")
    @RequireRole("hod")
    public ResponseEntity<Map<String, Object>> runStatusJobManually() {
        log.info("On-demand status & completion transition job triggered manually by HOD administration account.");
        int transitionCount = statusService.runStatusTransitionCheck();
        int completionCount = completionService.runCompletionTransitionCheck();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Status and completion evaluation audit completed successfully");
        response.put("transitionCount", transitionCount);
        response.put("completionCount", completionCount);
        response.put("timestamp", ZonedDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
