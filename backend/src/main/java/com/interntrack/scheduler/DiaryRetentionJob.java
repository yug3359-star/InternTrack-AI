package com.interntrack.scheduler;

import com.interntrack.service.DiaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DiaryRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DiaryRetentionJob.class);

    @Autowired
    private DiaryService diaryService;

    /**
     * Scheduled nightly cleanup job that runs daily at 02:00 AM.
     * Enforces the 14-day data retention policy on accepted student logs in Firestore.
     * STRICT INSTITUTIONAL RULE: Only accepted documents older than 14 real days are deleted.
     * Suspicious logs in suspicious_diaries are retained permanently for institutional investigation and escalation.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void performNightlyRetentionCleanup() {
        log.info("=================================================================================");
        log.info("[SCHEDULED RETENTION JOB] Starting automated 14-day pruning of accepted diaries...");
        try {
            diaryService.execute14DayRetentionCleanup();
            log.info("[SCHEDULED RETENTION JOB] Pruning cycle successfully executed.");
        } catch (Exception e) {
            log.error("[SCHEDULED RETENTION JOB] Error executing database pruning: {}", e.getMessage(), e);
        }
        log.info("=================================================================================");
    }
}
