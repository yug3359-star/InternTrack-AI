package com.interntrack.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.exception.InvalidRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared institutional quota management service (Module 5a & Module 5b).
 * Tracks monthly meeting override exemptions across random working hour check-ins and formal daily attendance.
 * Directly integrates with real Firestore documents at 'quotas/{uid}_{yyyy-MM}'.
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);
    private static final int MONTHLY_MEETING_QUOTA = 3;
    private static final DateTimeFormatter MONTH_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");

    @Autowired(required = false)
    private Firestore firestore;

    private final ZoneId applicationZoneId = ZoneId.of("Asia/Kolkata");

    // Resilient local memory store for offline testing or fallback demonstrations
    private final Map<String, Map<String, Object>> localQuotaLedger = new ConcurrentHashMap<>();

    /**
     * Helper to reliably get Firestore instance if connected
     */
    private Firestore getDb() {
        if (this.firestore != null) return this.firestore;
        try {
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generates the shared document key (e.g. 'dev-stud-107_2026-07')
     */
    public String getQuotaDocId(String uid, LocalDate date) {
        return uid + "_" + date.format(MONTH_ID_FORMATTER);
    }

    /**
     * Retrieves current monthly meeting quota status for a student from real Firestore
     */
    public Map<String, Object> getStudentMeetingQuota(String uid) {
        LocalDate now = LocalDate.now(applicationZoneId);
        String docId = getQuotaDocId(uid, now);
        String monthDisplay = now.format(MONTH_DISPLAY_FORMATTER);

        Firestore db = getDb();
        if (db != null) {
            try {
                DocumentSnapshot doc = db.collection("quotas").document(docId).get().get();
                if (doc.exists() && doc.getData() != null) {
                    Map<String, Object> data = new HashMap<>(doc.getData());
                    // Guarantee consistent schema fields for frontends and calculations
                    int limit = MONTHLY_MEETING_QUOTA;
                    int rawUsed = ((Number) data.getOrDefault("used", 0)).intValue();
                    int used = Math.min(rawUsed, limit); // Enterprise graceful downgrade if limit was reduced
                    int remaining = Math.max(0, limit - used);
                    data.put("used", used);
                    data.put("limit", limit);
                    data.put("remaining", remaining);
                    data.put("meeting_quota_remaining", remaining);
                    data.put("month", data.getOrDefault("month", monthDisplay));
                    return data;
                } else {
                    // Initialize real doc in Firestore if first time accessed this month
                    Map<String, Object> initialQuota = buildInitialQuota(uid, monthDisplay, now.format(MONTH_ID_FORMATTER));
                    db.collection("quotas").document(docId).set(initialQuota);
                    log.info("Initialized real monthly meeting quota doc in Firestore: quotas/{}", docId);
                    return initialQuota;
                }
            } catch (Exception e) {
                log.warn("Firestore unreachable for quota read on doc [{}]. Falling back to local ledger: {}", docId, e.getMessage());
            }
        }

        // Offline or dev fallback evaluation
        if (!localQuotaLedger.containsKey(docId)) {
            localQuotaLedger.put(docId, buildInitialQuota(uid, monthDisplay, now.format(MONTH_ID_FORMATTER)));
        }
        return localQuotaLedger.get(docId);
    }

    private Map<String, Object> buildInitialQuota(String uid, String monthDisplay, String monthId) {
        Map<String, Object> q = new HashMap<>();
        q.put("uid", uid);
        q.put("month", monthDisplay);
        q.put("monthId", monthId);
        // By default starting with 2 used in demo accounts to show active utilization
        int initialUsed = uid.startsWith("dev-stud-") ? 2 : 0;
        q.put("used", initialUsed);
        q.put("limit", MONTHLY_MEETING_QUOTA);
        q.put("remaining", MONTHLY_MEETING_QUOTA - initialUsed);
        q.put("meeting_quota_remaining", MONTHLY_MEETING_QUOTA - initialUsed);
        return q;
    }

    /**
     * Consumes 1 meeting override quota pass from the shared monthly pool.
     * Returns true if successfully decremented, false if quota remaining is 0.
     */
    public synchronized boolean tryConsumeMeetingQuota(String uid) {
        LocalDate now = LocalDate.now(applicationZoneId);
        String docId = getQuotaDocId(uid, now);
        Map<String, Object> currentQuota = getStudentMeetingQuota(uid);

        int limit = MONTHLY_MEETING_QUOTA;
        int rawUsed = ((Number) currentQuota.getOrDefault("used", 0)).intValue();
        int used = Math.min(rawUsed, limit);
        int remaining = Math.max(0, limit - used);

        if (remaining <= 0 || used >= limit) {
            log.warn("Meeting override denied for student [{}] - quota exhausted ({}/{} used).", uid, used, limit);
            return false;
        }

        int newUsed = used + 1;
        int newRemaining = Math.max(0, limit - newUsed);
        currentQuota.put("used", newUsed);
        currentQuota.put("remaining", newRemaining);
        currentQuota.put("meeting_quota_remaining", newRemaining);

        localQuotaLedger.put(docId, currentQuota);

        Firestore db = getDb();
        if (db != null) {
            try {
                db.collection("quotas").document(docId).set(currentQuota);
                log.info("Committed meeting quota decrement in real Firestore doc quotas/{}. Status: {}/{} used.", docId, newUsed, limit);
            } catch (Exception e) {
                log.error("Failed to write updated quota to cloud Firestore doc quotas/{}: {}", docId, e.getMessage());
            }
        }
        return true;
    }

    /**
     * Consumes quota or throws explicit verification exception if exhausted.
     */
    public void consumeMeetingQuotaOrThrow(String uid) throws InvalidRegistrationException {
        if (!tryConsumeMeetingQuota(uid)) {
            Map<String, Object> q = getStudentMeetingQuota(uid);
            int limit = MONTHLY_MEETING_QUOTA;
            throw new InvalidRegistrationException("Monthly meeting override quota exhausted (" + limit + "/" + limit + " used). Webcam or attendance verification is strictly required.");
        }
    }
}
