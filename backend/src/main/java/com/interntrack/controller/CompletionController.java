package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.CompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Thin REST controller exposing endpoints for student completion certification reports
 * and Head of Department completed candidate administrative ledgers.
 */
@RestController
@RequestMapping("/api")
public class CompletionController {

    private static final Logger log = LoggerFactory.getLogger(CompletionController.class);

    private final CompletionService completionService;

    public CompletionController(CompletionService completionService) {
        this.completionService = completionService;
    }

    /**
     * Retrieves permanent stored completion summary certificate for a candidate.
     * Returns 404 Not Found if the candidate has not yet completed their internship.
     */
    @GetMapping("/completion/{uid}")
    public ResponseEntity<Map<String, Object>> getCompletionSummary(@PathVariable String uid) {
        Map<String, Object> summary = completionService.getCompletionSummary(uid);
        if (summary == null) {
            log.info("Completion report requested for candidate [{}], but no completed certification ledger was found.", uid);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(summary);
    }

    /**
     * Retrieves official list of all students whose internship tenure has formally concluded as completed.
     * Enforces Department Head role authorization via @RequireRole.
     */
    @GetMapping("/hod/completed")
    @RequireRole("hod")
    public ResponseEntity<List<Map<String, Object>>> getCompletedStudents() {
        return ResponseEntity.ok(completionService.getAllCompletedStudents());
    }
}
