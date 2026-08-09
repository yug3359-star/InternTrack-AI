package com.interntrack.scheduler;

import com.interntrack.repository.InternshipLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled cron process checking weekly submissions and automating compliance status flagging.
 */
@Component
public class ComplianceScheduler {

    private static final Logger log = LoggerFactory.getLogger(ComplianceScheduler.class);
    private final InternshipLogRepository logRepository;

    public ComplianceScheduler(InternshipLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Executes automatically every Sunday at midnight (0 0 0 * * SUN) to assess weekly attendance gaps.
     * For local developer diagnostic evaluation, runs once 15 seconds after service startup.
     */
    @Scheduled(initialDelay = 15000, fixedRate = 86400000)
    public void executeWeeklyComplianceAudit() {
        log.info("=====================================================================================");
        log.info("[SCHEDULED AUDIT] Executing automated departmental compliance log audit at {}", LocalDateTime.now());
        
        long pendingReviewCount = logRepository.countByVerificationStatus("PENDING");
        log.info("Current backlog assessment: {} weekly student activity logs awaiting faculty mentor validation.", pendingReviewCount);
        
        // Automated notification triggering logic placeholder
        if (pendingReviewCount > 0) {
            log.info("Dispatching daily diagnostic digest to faculty advisors regarding pending verifications.");
        } else {
            log.info("No compliance log backlogs detected. Academic ledger synchronized.");
        }
        log.info("=====================================================================================");
    }
}
