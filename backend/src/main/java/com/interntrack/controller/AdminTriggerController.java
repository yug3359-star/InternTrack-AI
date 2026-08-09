package com.interntrack.controller;

import com.interntrack.scheduler.WeeklyQuestionGenJob;
import com.interntrack.security.RequireRole;
import com.interntrack.security.SecurityAuditLogger;
import com.interntrack.service.AiPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HOD Administrative trigger controller for manual testing and verification of AI compliance pipelines.
 * Enforces mandatory HOD role verification via @RequireRole("hod") AOP architecture.
 */
@RestController
@RequestMapping("/api/hod/triggers")
@RequireRole("hod")
public class AdminTriggerController {

    private final WeeklyQuestionGenJob weeklyQuestionGenJob;
    private final AiPipelineService aiPipelineService;
    private final SecurityAuditLogger auditLogger;

    public AdminTriggerController(WeeklyQuestionGenJob weeklyQuestionGenJob,
                                  AiPipelineService aiPipelineService,
                                  SecurityAuditLogger auditLogger) {
        this.weeklyQuestionGenJob = weeklyQuestionGenJob;
        this.aiPipelineService = aiPipelineService;
        this.auditLogger = auditLogger;
    }

    /**
     * Manually forces weekly AI test question generation for a targeted student ID.
     */
    @PostMapping("/generate-weekly-questions/{studentUid}")
    public ResponseEntity<Map<String, Object>> triggerWeeklyQuestionGenForStudent(@PathVariable String studentUid) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        
        auditLogger.logAdminAction("MANUAL_AI_QUESTION_GEN", hodUid, studentUid, "Manually triggered Module 6 AI question generation pipeline");
        Map<String, Object> result = aiPipelineService.generateWeeklyTestQuestions(studentUid).get();
        return ResponseEntity.ok(result);
    }

    /**
     * Manually invokes the system-wide weekly question generation batch job.
     */
    @PostMapping("/generate-weekly-questions")
    public ResponseEntity<Map<String, String>> triggerWeeklyQuestionGenBatch() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hodUid = (auth != null && auth.getName() != null) ? auth.getName() : "hod-directory-admin";
        
        auditLogger.logAdminAction("MANUAL_BATCH_QUESTION_GEN", hodUid, "SYSTEM_WIDE", "Manually executed weekly AI question generation scheduler job");
        weeklyQuestionGenJob.generateWeeklyQuestionBanks();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Dispatched weekly AI examination generation across all active internships."));
    }
}
