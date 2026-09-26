package com.interntrack.scheduler;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.service.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Scheduled cron engine governing Module 5c twice-weekly proctored examinations and automated window expiry cleanup.
 */
@Component
public class TestSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(TestSchedulerJob.class);

    @Autowired
    private TestService testService;

    @Autowired(required = false)
    private Firestore firestore;

    private final ZoneId zoneId = ZoneId.of("Asia/Kolkata");

    private Firestore getDb() {
        if (this.firestore != null) return this.firestore;
        try {
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Executes twice weekly (e.g. Mondays and Thursdays at 10:00 AM) to instantiate proctored exams
     * strictly for students whose active internship status is "Ongoing".
     */
    @Scheduled(cron = "${interntrack.test.schedule-cron:0 0 10 * * MON,THU}")
    public void triggerTwiceWeeklyTests() {
        log.info("=========================================================================================");
        log.info("[SCHEDULED EXAM ENGINE] Launching twice-weekly proctored test trigger for Ongoing cohort");
        LocalDate today = LocalDate.now(zoneId);
        String todayStr = today.toString();
        long now = System.currentTimeMillis();

        int triggeredCount = 0;
        Firestore db = getDb();
        if (db != null) {
            try {
                var query = db.collection("internships").whereEqualTo("status", "Ongoing").get().get();
                for (QueryDocumentSnapshot doc : query.getDocuments()) {
                    Map<String, Object> data = doc.getData();
                    if (data != null) {
                        String uid = (String) data.getOrDefault("uid", doc.getId());
                        String domain = (String) data.getOrDefault("internshipDomain", "Software Architecture");
                        String refUrl = (String) data.getOrDefault("referencePhotoUrl", "https://firebasestorage.googleapis.com/v0/b/interntrack-dev.appspot.com/o/reference-photos%2F" + uid + ".jpg");
                        
                        testService.createTestDoc(uid, todayStr, now, 60, domain, refUrl);
                        triggeredCount++;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not query Firestore internships collection during test trigger: {}", e.getMessage());
            }
        }

        // Removed dev mock fallbacks

        log.info("Twice-weekly proctored exams created for {} Ongoing student practitioner(s) with 1-hour start windows.", triggeredCount);
        log.info("=========================================================================================");
    }

    /**
     * Rapid automated cleanup job executing every 3 minutes.
     * Sweeps active collection to mark expired start windows or exceeded exam deadlines as 'absent'.
     */
    @Scheduled(fixedRate = 180000)
    public void executeTestTimeoutCleanup() {
        log.debug("Executing scheduled sweep for expired proctored exam windows and server deadlines...");
        try {
            testService.cleanupExpiredTests();
        } catch (Exception e) {
            log.error("Exception during automated test timeout cleanup: {}", e.getMessage());
        }
    }
}
