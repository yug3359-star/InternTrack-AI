package com.interntrack.scheduler;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.service.DailyStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.annotation.PostConstruct;

/**
 * Scheduled cron job managing formal daily attendance marking (Module 5b).
 * Issues 1x daily attendance checks strictly tied to a student's working hours.
 * 100% Firestore-backed for production crash resiliency.
 */
@Component
public class AttendancePopupJob {

    private static final Logger log = LoggerFactory.getLogger(AttendancePopupJob.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long WINDOW_MS = 3600_000L; // 1 hour in milliseconds

    @Autowired(required = false)
    private Firestore firestore;

    @Autowired(required = false)
    private DailyStatusService dailyStatusService;

    @Autowired(required = false)
    private EngagementPopupJob engagementPopupJob;

    private final ZoneId applicationZoneId = ZoneId.of("Asia/Kolkata");

    public AttendancePopupJob() {
    }

    private Firestore getDb() {
        if (this.firestore != null) return this.firestore;
        try {
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Initializes the attendance records. Runs on startup and hourly for all ongoing students.
     * Skips students who already have a schedule for today.
     */
    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    public void generateDailyAttendanceChecks() {
        log.info("Midnight init: Creating scheduled attendance windows for Ongoing internships.");
        Firestore db = getDb();
        if (db == null) return;

        Map<String, DocumentSnapshot> ongoingDocs = new HashMap<>();
        try {
            ApiFuture<QuerySnapshot> future = db.collection("internships").whereEqualTo("status", "Ongoing").get();
            for (DocumentSnapshot d : future.get().getDocuments()) {
                ongoingDocs.put(d.getId(), d);
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore unreachable during ongoing internship query: {}", e.getMessage());
        }

        // Removed dev mock fallbacks

        LocalDate today = LocalDate.now(applicationZoneId);
        String todayStr = today.format(DATE_FORMATTER);
        String currentDayName = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

        Set<String> existingUids = new HashSet<>();
        try {
            ApiFuture<QuerySnapshot> existingFuture = db.collection("attendance").whereEqualTo("date", todayStr).get();
            for (QueryDocumentSnapshot doc : existingFuture.get().getDocuments()) {
                existingUids.add(doc.getString("uid"));
            }
        } catch (Exception e) {}

        for (Map.Entry<String, DocumentSnapshot> entry : ongoingDocs.entrySet()) {
            String uid = entry.getKey();
            if (existingUids.contains(uid)) continue;
            DocumentSnapshot d = entry.getValue();

            if (d != null && d.contains("workingDays")) {
                List<String> workingDays = (List<String>) d.get("workingDays");
                if (workingDays != null && !workingDays.isEmpty() && !workingDays.contains(currentDayName)) {
                    log.info("Skipping attendance window init for student [{}] because today ({}) is a rest day.", uid, currentDayName);
                    continue;
                }
            }

            String startStr = (d != null && d.getString("officeStartTime") != null) ? d.getString("officeStartTime") : "09:00";
            String[] parts = startStr.split(":");
            LocalDateTime startDateTime = today.atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            long windowStart = startDateTime.atZone(applicationZoneId).toInstant().toEpochMilli();
            long deadline = windowStart + WINDOW_MS;

            String docId = uid + "_" + todayStr;

            Map<String, Object> attDoc = new HashMap<>();
            attDoc.put("id", docId);
            attDoc.put("uid", uid);
            attDoc.put("date", todayStr);
            attDoc.put("status", "scheduled"); // Will be activated exactly at shift start
            attDoc.put("windowStart", windowStart);
            attDoc.put("deadline", deadline);
            attDoc.put("windowMinutes", 60);

            try {
                db.collection("attendance").document(docId).set(attDoc);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Runs every minute to strictly open the 1-hour window exactly when a student's shift starts.
     */
    @Scheduled(cron = "0 * * * * *")
    public void dispatchAttendanceWindows() {
        long currentMillis = System.currentTimeMillis();
        String todayStr = LocalDate.now(applicationZoneId).format(DATE_FORMATTER);
        Firestore db = getDb();
        if (db == null) return;

        try {
            ApiFuture<QuerySnapshot> future = db.collection("attendance")
                .whereEqualTo("date", todayStr)
                .whereEqualTo("status", "scheduled")
                .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Long windowStart = doc.getLong("windowStart");
                Long deadline = doc.getLong("deadline");
                if (windowStart != null && currentMillis >= windowStart) {
                    if (deadline != null && currentMillis > deadline) {
                        log.warn("Shift started in the past, but 1-hour window strictly expired for [{}]. Marking MISSED immediately.", doc.getId());
                        transitionToMissed(doc.getId(), doc.getString("uid"), doc.getString("date"));
                    } else {
                        log.info("Shift started! Opening 1-hour attendance window for [{}]", doc.getId());
                        doc.getReference().update(
                            "status", "awaiting_response",
                            "triggeredAt", currentMillis
                        );
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Cleanup job running every 3 minutes to inspect records past their strict 1-hour window.
     */
    @Scheduled(fixedRate = 180_000)
    public void cleanupExpiredAttendanceWindows() {
        long currentMillis = System.currentTimeMillis();
        Firestore db = getDb();
        if (db == null) return;

        try {
            ApiFuture<QuerySnapshot> future = db.collection("attendance")
                .whereEqualTo("status", "awaiting_response")
                .get();
            
            for (QueryDocumentSnapshot d : future.get().getDocuments()) {
                Long deadline = d.getLong("deadline");
                if (deadline != null && currentMillis > deadline) {
                    transitionToMissed(d.getId(), d.getString("uid"), d.getString("date"));
                }
            }
        } catch (Exception ignored) {}
    }

    private void transitionToMissed(String docId, String uid, String dateStr) {
        log.warn("Strict 1-hour attendance check window expired for [{}]. Marking status as MISSED.", docId);
        Firestore db = getDb();
        if (db != null) {
            try {
                db.collection("attendance").document(docId).update("status", "missed", "closedAt", System.currentTimeMillis());
            } catch (Exception ignored) {}
        }

        if (dailyStatusService != null && uid != null && dateStr != null) {
            try {
                int missedPopups = engagementPopupJob != null ? engagementPopupJob.getMissedCountToday(uid) : 0;
                dailyStatusService.evaluateDailyStatus(uid, LocalDate.parse(dateStr, DATE_FORMATTER), missedPopups);
            } catch (Exception e) {
                log.error("Error evaluating daily status after attendance miss: {}", e.getMessage());
            }
        }
    }

    /**
     * Daily midnight auditor that backfills any missing attendance records from the student's
     * start date up until yesterday. Ensures a perfect mathematical audit trail even if the server was offline.
     */
    @PostConstruct
    @Scheduled(cron = "0 5 0 * * *") // Runs 5 minutes after midnight
    public void auditAndBackfillMissingDays() {
        log.info("Executing daily formal attendance backfill audit.");
        Firestore db = getDb();
        if (db == null) return;

        try {
            ApiFuture<QuerySnapshot> future = db.collection("internships").whereEqualTo("status", "Ongoing").get();
            LocalDate today = LocalDate.now(applicationZoneId);

            for (QueryDocumentSnapshot d : future.get().getDocuments()) {
                String uid = d.getId();
                
                // Attempt to parse 'joiningDate', default to 7 days ago if missing
                LocalDate startDate = today.minusDays(7); 
                String startDateStr = d.getString("joiningDate");
                if (startDateStr != null) {
                    try {
                        startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
                    } catch (Exception ignored) {}
                }

                // Fetch all existing attendance dates for this UID
                Set<String> existingDates = new HashSet<>();
                ApiFuture<QuerySnapshot> attFuture = db.collection("attendance").whereEqualTo("uid", uid).get();
                for (QueryDocumentSnapshot att : attFuture.get().getDocuments()) {
                    existingDates.add(att.getString("date"));
                }

                // Iterate from startDate to yesterday
                LocalDate currentDate = startDate;
                while (currentDate.isBefore(today)) {
                    String dateStr = currentDate.format(DATE_FORMATTER);
                    String dayName = currentDate.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

                    if (d != null && d.contains("workingDays")) {
                        List<String> workingDays = (List<String>) d.get("workingDays");
                        if (workingDays != null && !workingDays.isEmpty() && !workingDays.contains(dayName)) {
                            currentDate = currentDate.plusDays(1);
                            continue;
                        }
                    }

                    String docId = uid + "_" + dateStr;

                    if (!existingDates.contains(dateStr)) {
                        log.warn("Backfill Audit: Missing attendance record found for [{}] on [{}]. Marking as missed.", uid, dateStr);
                        Map<String, Object> record = new HashMap<>();
                        record.put("id", docId);
                        record.put("uid", uid);
                        record.put("date", dateStr);
                        record.put("status", "missed");
                        record.put("closedAt", System.currentTimeMillis());
                        record.put("auditNote", "Auto-generated by Daily Backfill Auditor due to missing historical record.");
                        
                        db.collection("attendance").document(docId).set(record);
                        
                        if (dailyStatusService != null) {
                            dailyStatusService.evaluateDailyStatus(uid, currentDate, 0);
                        }
                    }
                    currentDate = currentDate.plusDays(1);
                }
            }
        } catch (Exception e) {
            log.error("Error executing attendance backfill audit: {}", e.getMessage());
        }
    }

    public Map<String, Object> getAttendanceRecord(String docId) {
        Firestore db = getDb();
        if (db != null) {
            try {
                DocumentSnapshot d = db.collection("attendance").document(docId).get().get();
                if (d.exists() && d.getData() != null) {
                    return d.getData();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void saveAttendanceRecord(String docId, Map<String, Object> rec) {
        Firestore db = getDb();
        if (db != null) {
            try {
                db.collection("attendance").document(docId).set(rec);
            } catch (Exception ignored) {}
        }
    }

    public List<Map<String, Object>> getStudentHistory(String uid) {
        List<Map<String, Object>> list = new ArrayList<>();
        Firestore db = getDb();
        if (db != null) {
            try {
                ApiFuture<QuerySnapshot> future = db.collection("attendance").whereEqualTo("uid", uid).get();
                List<? extends DocumentSnapshot> docs = future.get().getDocuments();
                for (DocumentSnapshot d : docs) {
                    if (d.getData() != null) {
                        Map<String, Object> rec = d.getData();
                        // Ignore "scheduled" ones so frontend doesn't show them early
                        if (!"scheduled".equals(rec.get("status"))) {
                            
                            // Cross-reference authoritative daily status for compliance overrides (e.g. AI Diary Rejection)
                            try {
                                DocumentSnapshot ds = db.collection("daily_status").document(d.getId()).get().get();
                                if (ds.exists()) {
                                    String effectiveStatus = ds.getString("status");
                                    if ("absent".equalsIgnoreCase(effectiveStatus) || "missed".equalsIgnoreCase(effectiveStatus) || "excused_meeting".equalsIgnoreCase(effectiveStatus)) {
                                        rec.put("status", effectiveStatus);
                                    } else if ("present".equalsIgnoreCase(effectiveStatus) && !rec.containsKey("respondedAt")) {
                                        // Optional: if it was somehow strictly overridden to present, though attendance itself is the primary driver
                                    }
                                }
                            } catch (Exception ignored) {}

                            list.add(rec);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        list.sort((a, b) -> String.valueOf(b.getOrDefault("date", "")).compareTo(String.valueOf(a.getOrDefault("date", ""))));
        return list;
    }

    public Map<String, Object> triggerAttendanceForToday(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DATE_FORMATTER);
        String docId = uid + "_" + todayStr;
        long now = System.currentTimeMillis();

        String startStr = "09:00"; // default
        Firestore db = getDb();
        if (db != null) {
            try {
                DocumentSnapshot internDoc = db.collection("internships").document(uid).get().get();
                if (internDoc.exists()) {
                    if (internDoc.getString("officeStartTime") != null) {
                        startStr = internDoc.getString("officeStartTime");
                    }
                    if (internDoc.contains("workingDays")) {
                        List<String> workingDays = (List<String>) internDoc.get("workingDays");
                        String currentDayName = LocalDate.now(applicationZoneId).getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
                        if (workingDays != null && !workingDays.isEmpty() && !workingDays.contains(currentDayName)) {
                            log.info("On-demand attendance trigger aborted for [{}] because today ({}) is a rest day.", uid, currentDayName);
                            return null;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        
        String[] parts = startStr.split(":");
        LocalDateTime startDateTime = LocalDate.now(applicationZoneId).atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        long windowStart = startDateTime.atZone(applicationZoneId).toInstant().toEpochMilli();
        long deadline = windowStart + WINDOW_MS;

        Map<String, Object> attDoc = new HashMap<>();
        attDoc.put("id", docId);
        attDoc.put("uid", uid);
        attDoc.put("date", todayStr);
        attDoc.put("windowStart", windowStart);
        attDoc.put("deadline", deadline);
        attDoc.put("windowMinutes", 60);

        if (now > deadline) {
            log.warn("Late fallback trigger: Marking attendance immediately missed for [{}]", docId);
            attDoc.put("status", "missed");
            attDoc.put("closedAt", now);
        } else {
            attDoc.put("status", "awaiting_response");
            attDoc.put("triggeredAt", now);
        }

        saveAttendanceRecord(docId, attDoc);
        
        if (now > deadline) {
            transitionToMissed(docId, uid, todayStr);
        }
        
        return attDoc;
    }
}
