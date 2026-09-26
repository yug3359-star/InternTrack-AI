package com.interntrack.scheduler;

import com.interntrack.service.EscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Automated daily cron worker responsible for running institutional escalation & highlighting audits.
 * Communicates with EscalationService to tally real candidate compliance ledgers across Firestore.
 */
@Component
public class EscalationJob {

    private static final Logger log = LoggerFactory.getLogger(EscalationJob.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EscalationService escalationService;

    public EscalationJob(EscalationService escalationService) {
        this.escalationService = escalationService;
    }

    /**
     * Scheduled automated execution running DAILY at 01:00 AM (cron = "0 0 1 * * *").
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void executeDailyEscalationAudit() {
        java.time.LocalDate today = java.time.LocalDate.now();
        String targetMonth;
        
        if (today.getDayOfMonth() == 1) {
            // On the 1st of the month at 1:00 AM, audit the preceding month to finalize its records
            targetMonth = YearMonth.now().minusMonths(1).format(MONTH_FORMATTER);
        } else {
            // Mid-month, continuously audit the current active month
            targetMonth = YearMonth.now().format(MONTH_FORMATTER);
        }

        log.info("===================================================================================");
        log.info("   CRON INITIALIZED: Starting automated daily escalation audit for [{}]          ", targetMonth);
        log.info("===================================================================================");

        int flaggedCount = escalationService.runEscalationCheck(targetMonth);

        log.info("Daily escalation cron completed successfully for month [{}]. [{}] total candidate records highlighted.", targetMonth, flaggedCount);
        log.info("===================================================================================");
    }
}
