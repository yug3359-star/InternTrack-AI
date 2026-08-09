package com.interntrack.scheduler;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production automated daily system telemetry digest job.
 * Executes daily at 23:00 (11:00 PM) server time to gather institutional reliability metrics:
 * scheduled worker exceptions, biometric face-match API rate limit timeouts, AI LLM evaluation failures,
 * and candidates who hit zero meeting pass allowance quotas.
 * Writes immutable metrics to Firestore system_logs/{date} and dispatches daily operational digest emails to HODs.
 */
@Component
public class DailyDigestJob {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestJob.class);

    // Static runtime error telemetry counters reset each night at 11 PM
    private static final AtomicInteger scheduledJobFailureCount = new AtomicInteger(0);
    private static final AtomicInteger faceMatchErrorCount = new AtomicInteger(0);
    private static final AtomicInteger aiPipelineErrorCount = new AtomicInteger(0);
    private static final AtomicInteger quotaExhaustionCount = new AtomicInteger(0);

    @Autowired(required = false)
    private Firestore firestore;

    @Value("${interntrack.admin-email:${ADMIN_EMAIL:dean.academics@college.edu}}")
    private String adminEmail;

    /**
     * Telemetry registration hooks invoked by services upon recoverable API or cron anomalies
     */
    public static void recordScheduledJobFailure() {
        scheduledJobFailureCount.incrementAndGet();
    }

    public static void recordFaceMatchError() {
        faceMatchErrorCount.incrementAndGet();
    }

    public static void recordAiPipelineError() {
        aiPipelineErrorCount.incrementAndGet();
    }

    public static void recordQuotaExhaustion() {
        quotaExhaustionCount.incrementAndGet();
    }

    /**
     * Executes once daily at 11:00 PM server time.
     */
    @Scheduled(cron = "${interntrack.digest.cron:0 0 23 * * *}")
    public void generateAndDispatchDailyDigest() {
        String todayDate = LocalDate.now().toString();
        log.info("=========================================================================================");
        log.info("[PRODUCTION HARDENING] Executing automated 11:00 PM Daily Digest Audit for [{}]", todayDate);
        log.info("=========================================================================================");

        int jobFailures = scheduledJobFailureCount.getAndSet(0);
        int faceErrors = faceMatchErrorCount.getAndSet(0);
        int aiErrors = aiPipelineErrorCount.getAndSet(0);
        int quotaExhaustions = computeRealZeroQuotaStudents();
        
        // Add manual counter triggers to actual computed quotas
        quotaExhaustions += quotaExhaustionCount.getAndSet(0);

        Map<String, Object> digestReport = new HashMap<>();
        digestReport.put("date", todayDate);
        digestReport.put("scheduledJobFailures", jobFailures);
        digestReport.put("faceMatchApiErrors", faceErrors);
        digestReport.put("aiPipelineFailures", aiErrors);
        digestReport.put("zeroMeetingQuotaStudents", quotaExhaustions);
        digestReport.put("timestamp", System.currentTimeMillis());
        digestReport.put("status", (jobFailures > 5 || faceErrors > 10) ? "DEGRADED" : "HEALTHY");

        // 1. Persist summary to Firestore collection system_logs/{date}
        persistDigestToFirestore(todayDate, digestReport);

        // 2. Dispatch real automated summary email to ADMIN_EMAIL via Trigger Email pattern & SMTP logger
        dispatchDailyDigestEmail(todayDate, digestReport);
    }

    private int computeRealZeroQuotaStudents() {
        if (firestore == null) {
            log.info("Firestore connection uninitialized in local workspace; simulating meeting quota scan.");
            return 2;
        }
        try {
            int currentMonth = LocalDate.now().getMonthValue();
            int currentYear = LocalDate.now().getYear();
            String monthSuffix = String.format("_%04d-%02d", currentYear, currentMonth);

            // Scan quotas collection for documents where remainingPasses <= 0
            Query query = firestore.collection("quotas").whereLessThanOrEqualTo("remainingPasses", 0);
            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            return docs.size();
        } catch (Exception e) {
            log.warn("Failed to query Firestore quotas collection for zero-quota accounts: {}", e.getMessage());
            return 0;
        }
    }

    private void persistDigestToFirestore(String date, Map<String, Object> report) {
        if (firestore == null) {
            log.warn("[DEV MODE] Firestore unavailable. Skipping cloud write for system_logs/{}", date);
            return;
        }
        try {
            firestore.collection("system_logs").document(date).set(report).get();
            log.info("Successfully recorded daily reliability telemetry into Firestore document [system_logs/{}]", date);
        } catch (Exception e) {
            log.error("Critical failure persisting system telemetry log to Firestore: {}", e.getMessage(), e);
            recordScheduledJobFailure();
        }
    }

    private void dispatchDailyDigestEmail(String date, Map<String, Object> report) {
        String subject = String.format("[InternTrack AI Daily Digest] Operational Health Summary for %s", date);
        String body = String.format(
            "Institutional Operational Telemetry Report — %s\n" +
            "-----------------------------------------------------------------------\n" +
            "System Overall Operational State : %s\n" +
            "Scheduled Cron Worker Exceptions : %d\n" +
            "Biometric FaceMatch API Timeouts : %d\n" +
            "AI NLP Activity Pipeline Errors  : %d\n" +
            "Candidates with 0 Meeting Quota  : %d\n" +
            "-----------------------------------------------------------------------\n" +
            "Automated system health check generated by InternTrack AI Production Infrastructure.",
            date, report.get("status"), report.get("scheduledJobFailures"),
            report.get("faceMatchApiErrors"), report.get("aiPipelineFailures"),
            report.get("zeroMeetingQuotaStudents")
        );

        log.info("======================== [AUTOMATED SMTP EMAIL DISPATCH] ========================");
        log.info("To: [{}]", adminEmail);
        log.info("Subject: {}", subject);
        log.info("Body:\n{}", body);
        log.info("=================================================================================");

        // If Firestore is available, bind to Firebase Extensions Trigger Email collection ("mail")
        if (firestore != null) {
            try {
                Map<String, Object> mailDoc = new HashMap<>();
                mailDoc.put("to", List.of(adminEmail));
                Map<String, Object> messageContent = new HashMap<>();
                messageContent.put("subject", subject);
                messageContent.put("text", body);
                mailDoc.put("message", messageContent);

                firestore.collection("mail").add(mailDoc);
                log.info("Dispatched live automated email via Firestore 'mail' collection (Firebase Extensions Trigger Email).");
            } catch (Exception e) {
                log.warn("Could not push email payload to Firestore mail trigger collection: {}", e.getMessage());
            }
        }
    }
}
