package com.interntrack.scheduler;

import com.interntrack.service.CompletionService;
import com.interntrack.service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled cron worker executing automated lifecycle transitions across real Firestore student records.
 * Evaluates both Approved -> Ongoing check-in transitions and Ongoing -> Completed final compliance summaries.
 * Runs on hourly production cadence (0 0 * * * *) utilizing explicit server timezone configuration.
 */
@Component
public class StatusTransitionJob {

    private static final Logger log = LoggerFactory.getLogger(StatusTransitionJob.class);

    private final StatusService statusService;
    private final CompletionService completionService;

    public StatusTransitionJob(StatusService statusService, CompletionService completionService) {
        this.statusService = statusService;
        this.completionService = completionService;
    }

    /**
     * Hourly production cron execution evaluating Approved records against joining dates
     * and Ongoing records against completion dates.
     * Transitions applications cleanly through their academic lifecycle stages and stamps permanent compliance ledgers.
     */
    @Scheduled(cron = "0 * * * * *")
    public void executeHourlyStatusAudit() {
        log.info("===================================================================================");
        log.info("   CRON INITIALIZED: Starting hourly automated internship status & completion audit");
        log.info("===================================================================================");
        
        int ongoingCount = statusService.runStatusTransitionCheck();
        int completedCount = completionService.runCompletionTransitionCheck();

        log.info("Hourly cron cycle completed successfully. [{}] transitioned to ONGOING | [{}] transitioned to COMPLETED.", ongoingCount, completedCount);
        log.info("===================================================================================");
    }
}
