package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class DiaryService {

    private static final Logger log = LoggerFactory.getLogger(DiaryService.class);

    @Autowired(required = false)
    private Firestore firestore;

    @Autowired
    private AiPipelineService aiPipelineService;

    @Autowired(required = false)
    private DailyStatusService dailyStatusService;

    // In-memory simulation stores when Firestore admin runtime is in local development mode
    private final Map<String, Map<String, Object>> devDiaries = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> devSuspicious = new ConcurrentHashMap<>();
    private final Map<String, Integer> devWarnings = new ConcurrentHashMap<>();

    public DiaryService() {
        // Initialize baseline demonstrated evaluations for dev test student dev-stud-107
        // (Removed fake data initialization)
    }

    /**
     * Submits a student work diary and triggers automated mentor evaluation.
     */
    public synchronized Map<String, Object> submitDiary(String uid, String date, String entryText, boolean rejectedDueToFaceMismatch, String studentName) {
        String docId = uid + "_" + date;
        log.info("Processing diary submission for student [{}] on date [{}]. Face mismatch: {}", uid, date, rejectedDueToFaceMismatch);

        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        if (!today.equals(date)) {
            log.warn("Diary submission rejected: Provided date [{}] does not match current system date [{}].", date, today);
            throw new IllegalStateException("Diary entries can only be submitted for the current day.");
        }

        // 1. Check for resubmission attempt on already decided date
        if (isAlreadyDecided(docId)) {
            log.warn("Resubmission rejected: Date [{}] already has a locked Accepted or Rejected record.", date);
            throw new IllegalStateException("Resubmissions are prohibited for dates marked as Rejected or Accepted.");
        }

        long now = System.currentTimeMillis();
        Map<String, Object> record = new HashMap<>();
        record.put("id", docId);
        record.put("uid", uid);
        record.put("studentName", studentName != null && !studentName.isEmpty() ? studentName : "Alex Vance (2023CSB104)");
        record.put("date", date);
        record.put("entryText", entryText);
        record.put("submittedAt", now);

        // 2. Handle immediate Face-Match failure rejection route (Module 5a/5c integration)
        if (rejectedDueToFaceMismatch) {
            log.warn("Routing diary directly to suspicious_diaries due to biometric verification mismatch.");
            record.put("status", "rejected");
            record.put("reviewReason", "Face match verification failed");
            record.put("topics", List.of("Biometric Verification Failure", "Security Flag"));
            record.put("flaggedAt", now);

            saveToSuspicious(docId, record);
            incrementMonthlyWarningCounter(uid);
            return record;
        }

        // 3. Invoke consolidated LLM review & topic extraction pipeline
        try {
            Map<String, Object> aiResult = aiPipelineService.reviewAndExtractTopics(uid, entryText, date).get();
            String decision = (String) aiResult.getOrDefault("decision", "accept");
            String reason = (String) aiResult.getOrDefault("reason", "Automated Compliance Evaluation Complete");
            Object topics = aiResult.getOrDefault("topics", List.of("Software Engineering Ledger"));

            record.put("reviewReason", reason);
            record.put("topics", topics);

            if ("reject".equalsIgnoreCase(decision)) {
                log.info("AI evaluation rejected diary submission for domain mismatch or discontinuity.");
                record.put("status", "rejected");
                record.put("flaggedAt", now);
                saveToSuspicious(docId, record);
                incrementMonthlyWarningCounter(uid);
            } else {
                log.info("AI evaluation accepted diary submission.");
                record.put("status", "accepted");
                saveToDiaries(docId, record);
            }
        } catch (Exception e) {
            log.error("Error awaiting AI evaluation pipeline: {}", e.getMessage(), e);
            record.put("status", "accepted");
            record.put("reviewReason", "Accepted via emergency automated fallback; AI analysis timed out.");
            record.put("topics", List.of("Engineering Development Log"));
            saveToDiaries(docId, record);
        }

        return record;
    }

    /**
     * Increments real diaryRejectionCount on monthly warnings/{uid}_{yyyy-MM} doc.
     * This prepares the exact institutional escalation layer read by Module 8.
     */
    public void incrementMonthlyWarningCounter(String uid) {
        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String warnId = uid + "_" + month;
        log.info("Incrementing monthly escalation counter [diaryRejectionCount] for warning document [{}]", warnId);

        if (firestore != null) {
            try {
                DocumentReference docRef = firestore.collection("warnings").document(warnId);
                DocumentSnapshot doc = docRef.get().get();
                if (doc.exists()) {
                    docRef.update("diaryRejectionCount", FieldValue.increment(1));
                } else {
                    Map<String, Object> initial = new HashMap<>();
                    initial.put("uid", uid);
                    initial.put("month", month);
                    initial.put("diaryRejectionCount", 1L);
                    initial.put("updatedAt", System.currentTimeMillis());
                    docRef.set(initial);
                }
            } catch (Exception e) {
                log.warn("Unable to increment Firestore warning counter: {}", e.getMessage());
            }
        } else {
            devWarnings.put(warnId, devWarnings.getOrDefault(warnId, 0) + 1);
            log.info("Dev simulation warnings updated for [{}]: Total rejections this month = {}", warnId, devWarnings.get(warnId));
        }
    }

    private boolean isAlreadyDecided(String docId) {
        if (firestore != null) {
            try {
                DocumentSnapshot d = firestore.collection("diaries").document(docId).get().get();
                if (d.exists() && "accepted".equalsIgnoreCase(d.getString("status"))) return true;
                DocumentSnapshot s = firestore.collection("suspicious_diaries").document(docId).get().get();
                if (s.exists()) return true;
            } catch (Exception e) {
                log.warn("Error checking existing decided status: {}", e.getMessage());
            }
        }
        return devDiaries.containsKey(docId) || devSuspicious.containsKey(docId);
    }

    private void saveToDiaries(String docId, Map<String, Object> record) {
        if (firestore != null) {
            try {
                firestore.collection("diaries").document(docId).set(record);
                // Ensure no conflicting legacy record exists in suspicious collection
                firestore.collection("suspicious_diaries").document(docId).delete();
            } catch (Exception e) {
                log.warn("Error saving accepted diary to Firestore: {}", e.getMessage());
            }
        }
        devDiaries.put(docId, record);
        devSuspicious.remove(docId);
    }

    private void saveToSuspicious(String docId, Map<String, Object> record) {
        if (firestore != null) {
            try {
                firestore.collection("suspicious_diaries").document(docId).set(record);
                // Ensure original diaries doc is deleted/marked so it cannot be resubmitted
                firestore.collection("diaries").document(docId).delete();
            } catch (Exception e) {
                log.warn("Error saving suspicious diary to Firestore: {}", e.getMessage());
            }
        }
        devSuspicious.put(docId, record);
        devDiaries.remove(docId);
    }

    /**
     * Retrieves combined diary history (both Accepted and Rejected records) for student timeline display.
     */
    public List<Map<String, Object>> getStudentDiaries(String uid) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (firestore != null) {
            try {
                List<QueryDocumentSnapshot> acc = firestore.collection("diaries").whereEqualTo("uid", uid).get().get().getDocuments();
                for (QueryDocumentSnapshot d : acc) results.add(d.getData());

                List<QueryDocumentSnapshot> rej = firestore.collection("suspicious_diaries").whereEqualTo("uid", uid).get().get().getDocuments();
                for (QueryDocumentSnapshot d : rej) results.add(d.getData());
            } catch (Exception e) {
                log.warn("Error querying Firestore student diaries: {}", e.getMessage());
            }
        }

        if (results.isEmpty()) {
            // Return simulation ledger entries
            for (Map<String, Object> d : devDiaries.values()) {
                if (uid.equalsIgnoreCase((String) d.get("uid")) || uid.startsWith("dev-stud-")) results.add(d);
            }
            for (Map<String, Object> d : devSuspicious.values()) {
                if (uid.equalsIgnoreCase((String) d.get("uid")) || uid.startsWith("dev-stud-")) results.add(d);
            }
        }

        // Sort descending by submittedAt timestamp or date
        results.sort((a, b) -> {
            Long tA = (Long) a.getOrDefault("submittedAt", 0L);
            Long tB = (Long) b.getOrDefault("submittedAt", 0L);
            return tB.compareTo(tA);
        });

        return results;
    }

    /**
     * Prunes accepted diary entries older than 14 real days from diaries collection.
     * STRICT RULE: Never delete or expire records from suspicious_diaries.
     */
    public void execute14DayRetentionCleanup() {
        long fourteenDaysAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000);
        log.info("[RETENTION CLEANUP] Pruning accepted diary logs older than timestamp [{}] (14 days)", fourteenDaysAgo);

        if (firestore != null) {
            try {
                List<QueryDocumentSnapshot> docs = firestore.collection("diaries")
                        .whereEqualTo("status", "accepted")
                        .whereLessThan("submittedAt", fourteenDaysAgo)
                        .get().get().getDocuments();
                for (QueryDocumentSnapshot d : docs) {
                    d.getReference().delete();
                    log.info("Expired and removed older accepted diary document [{}]", d.getId());
                }
            } catch (Exception e) {
                log.warn("Error running Firestore retention query: {}", e.getMessage());
            }
        }

        // Prune dev simulation store
        devDiaries.entrySet().removeIf(entry -> {
            Long sub = (Long) entry.getValue().getOrDefault("submittedAt", 0L);
            boolean old = sub < fourteenDaysAgo;
            if (old) log.info("Expired simulation diary record [{}]", entry.getKey());
            return old;
        });
    }

    /**
     * Daily audit job running at 18:00 to verify all active students submitted their daily work diary.
     * If they missed it, apply an absence penalty for the day.
     */
    @Scheduled(cron = "0 0 18 * * *")
    public void executeDailyDiaryAbsenceAudit() {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        log.info("Initiating daily absence audit for missing diary submissions on date [{}]", today);

        List<String> activeStudents = new ArrayList<>();
        if (firestore != null) {
            try {
                // Fetch all students with Ongoing internships
                List<QueryDocumentSnapshot> docs = firestore.collection("internships").whereEqualTo("status", "Ongoing").get().get().getDocuments();
                for (QueryDocumentSnapshot doc : docs) {
                    activeStudents.add(doc.getId());
                }
            } catch (Exception e) {
                log.warn("Could not query Firestore for active internships: {}", e.getMessage());
            }
        }
        
        if (activeStudents.isEmpty()) {
            activeStudents.add("dev-stud-107");
            activeStudents.add("dev-stud-102");
        }

        for (String uid : activeStudents) {
            String docId = uid + "_" + today;
            if (!isAlreadyDecided(docId)) {
                log.warn("COMPLIANCE VIOLATION: Student [{}] failed to submit daily diary for [{}]. Applying absence penalty.", uid, today);
                if (dailyStatusService != null) {
                    dailyStatusService.recordAttendanceResult(uid, today, "absent", "Failed to submit mandatory daily work diary.");
                }
            }
        }
    }

    public Map<String, Map<String, Object>> getDevSuspiciousRegistry() {
        return devSuspicious;
    }

    public void overrideSuspiciousToAccepted(String docId, String mentorUid) {
        Map<String, Object> record = devSuspicious.remove(docId);
        if (record != null) {
            record.put("status", "accepted");
            record.put("mentorOverridden", true);
            record.put("overriddenBy", mentorUid != null ? mentorUid : "Faculty Mentor");
            record.put("reviewReason", "Faculty Mentor Overrode AI Rejection (Verified compliance by " + (mentorUid != null ? mentorUid : "Faculty") + ")");
            devDiaries.put(docId, record);
        }
    }

    public void permanentlyDeleteSuspicious(String docId) {
        devSuspicious.remove(docId);
    }
}
