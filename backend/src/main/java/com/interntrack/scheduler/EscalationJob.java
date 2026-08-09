package com.interntrack.scheduler;

import com.interntrack.service.EscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Automated monthly cron worker responsible for running institutional escalation & highlighting audits.
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
     * Scheduled automated execution running on the 1st of every month at 01:00 AM (cron = "0 0 1 1 * *").
     * <p>
     * WHY RUN ON THE 1ST OF THE MONTH AT 01:00 AM?
     * By triggering early on the 1st day of the subsequent month rather than the final day of the active month,
     * the scheduler guarantees that all last-minute attendance check-ins, engagement popups, face-match validations,
     * and daily AI work diaries for the preceding month are fully finalized and committed to Firestore.
     * This avoids clipping real-time events that occur late on the last working day of the month.
     * </p>
     * <p>
     * WHY 3 SEPARATE COUNTERS INSTEAD OF A CONSOLIDATED SCORE?
     * Per institutional compliance guidelines and the original engineering specification, we track:
     * 1) diaryRejectionCount (threshold >= 3),
     * 2) absenceCount including face-match self-contradiction flags (threshold >= 3), and
     * 3) excuseAbusePercentage (threshold >= 25%)
     * as three independent and concurrent indicators. Combining these into a single numerical score obscures
     * root-cause behavioral failures. A student with 100% daily attendance who routinely fabricates technical diaries
     * requires technical mentorship intervention, whereas an absent student requires administrative warning. Independent
     * counters preserve precise diagnostic tags on the Head of Department dashboard.
     * </p>
     */
    @Scheduled(cron = "0 0 1 1 * *")
    public void executeMonthlyEscalationAudit() {
        // Audit the immediately preceding month upon entering the new calendar month
        String targetMonth = YearMonth.now().minusMonths(1).format(MONTH_FORMATTER);

        log.info("===================================================================================");
        log.info("   CRON INITIALIZED: Starting automated monthly escalation audit for [{}]          ", targetMonth);
        log.info("===================================================================================");

        int flaggedCount = escalationService.runEscalationCheck(targetMonth);

        log.info("Monthly escalation cron completed successfully for month [{}]. [{}] total candidate records highlighted.", targetMonth, flaggedCount);
        log.info("===================================================================================");
    }
}
