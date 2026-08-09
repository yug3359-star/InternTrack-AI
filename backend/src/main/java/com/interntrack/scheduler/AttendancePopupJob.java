package com.interntrack.scheduler;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.service.DailyStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled cron job managing formal daily attendance marking (Module 5b).
 * Issues 1x daily attendance checks (attendance/{uid}_{date}) with a 1-hour window for Ongoing students.
 * Continuously sweeps for expired windows, transitioning unanswered check-ins to 'missed' and recording daily absences.
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

    // Authoritative in-memory cache and simulation ledger
    private final Map<String, Map<String, Object>> attendanceLedger = new ConcurrentHashMap<>();

    public AttendancePopupJob() {
        initDefaultHistoricalRecords("dev-stud-107");
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
     * @Scheduled job triggering once daily per student (e.g. 09:30 AM server time, 30 min after standard 09:00 start)
     * exclusively for students with status == 'Ongoing' in real Firestore.
     */
    @Scheduled(cron = "0 30 9 * * *")
    public void generateDailyAttendanceChecks() {
        log.info("Executing daily formal attendance generation for all Ongoing internships (Timezone: [{}])", applicationZoneId.getId());
        Firestore db = getDb();

        List<String> ongoingUids = new ArrayList<>();
        if (db != null) {
            try {
                ApiFuture<QuerySnapshot> future = db.collection("internships").whereEqualTo("status", "Ongoing").get();
                List<? extends DocumentSnapshot> docs = future.get().getDocuments();
                for (DocumentSnapshot d : docs) {
                    ongoingUids.add(d.getId());
                }
            } catch (Exception e) {
                log.warn("Cloud Firestore unreachable during ongoing internship query: {}", e.getMessage());
            }
        }

        // Include default dev testing cohort if cloud query was empty or offline
        if (ongoingUids.isEmpty()) {
            ongoingUids.add("dev-stud-107");
        }

        for (String uid : ongoingUids) {
            triggerAttendanceForToday(uid);
        }
    }

    /**
     * Triggers today's attendance document for a student in real Firestore (also invokable via test endpoints).
     */
    public Map<String, Object> triggerAttendanceForToday(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DATE_FORMATTER);
        String docId = uid + "_" + todayStr;
        long now = System.currentTimeMillis();

        Map<String, Object> attDoc = new HashMap<>();
        attDoc.put("id", docId);
        attDoc.put("uid", uid);
        attDoc.put("date", todayStr);
        attDoc.put("status", "awaiting_response");
        attDoc.put("triggeredAt", now);
        attDoc.put("deadline", now + WINDOW_MS);
        attDoc.put("windowMinutes", 60);

        attendanceLedger.put(docId, attDoc);

        Firestore db = getDb();
        if (db != null) {
            try {
                db.collection("attendance").document(docId).set(attDoc);
                log.info("Created real attendance doc in Firestore: attendance/{} (Window: 1 hour)", docId);
            } catch (Exception e) {
                log.error("Failed to commit attendance check-in to Firestore doc {}: {}", docId, e.getMessage());
            }
        } else {
            log.info("Offline simulation: Generated attendance check-in for doc [{}]", docId);
        }

        return attDoc;
    }

    /**
     * Cleanup job running every 3 minutes to inspect records past their 1-hour window.
     * Transitions unaddressed 'awaiting_response' records to 'missed' and updates DailyStatusService.
     */
    @Scheduled(fixedRate = 180_000)
    public void cleanupExpiredAttendanceWindows() {
        long currentMillis = System.currentTimeMillis();
        Firestore db = getDb();

        // Step 1: Check cloud Firestore records if connected
        if (db != null) {
            try {
                ApiFuture<QuerySnapshot> future = db.collection("attendance").whereEqualTo("status", "awaiting_response").get();
                List<? extends DocumentSnapshot> docs = future.get().getDocuments();
                for (DocumentSnapshot d : docs) {
                    Long deadline = d.getLong("deadline");
                    if (deadline != null && currentMillis > deadline) {
                        transitionToMissed(d.getId(), d.getString("uid"), d.getString("date"));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Step 2: Check local tracking ledger
        for (Map.Entry<String, Map<String, Object>> entry : attendanceLedger.entrySet()) {
            Map<String, Object> rec = entry.getValue();
            if ("awaiting_response".equalsIgnoreCase(String.valueOf(rec.get("status")))) {
                long deadline = ((Number) rec.getOrDefault("deadline", Long.MAX_VALUE)).longValue();
                if (currentMillis > deadline) {
                    transitionToMissed(entry.getKey(), (String) rec.get("uid"), (String) rec.get("date"));
                }
            }
        }
    }

    private void transitionToMissed(String docId, String uid, String dateStr) {
        log.warn("Attendance check window expired for [{}]. Marking status as MISSED.", docId);
        Map<String, Object> rec = attendanceLedger.getOrDefault(docId, new HashMap<>());
        rec.put("status", "missed");
        rec.put("closedAt", System.currentTimeMillis());
        attendanceLedger.put(docId, rec);

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

    public Map<String, Object> getAttendanceRecord(String docId) {
        Firestore db = getDb();
        if (db != null) {
            try {
                DocumentSnapshot d = db.collection("attendance").document(docId).get().get();
                if (d.exists() && d.getData() != null) {
                    attendanceLedger.put(docId, new HashMap<>(d.getData()));
                    return d.getData();
                }
            } catch (Exception ignored) {}
        }
        return attendanceLedger.get(docId);
    }

    public void saveAttendanceRecord(String docId, Map<String, Object> rec) {
        attendanceLedger.put(docId, rec);
        Firestore db = getDb();
        if (db != null) {
            try {
                db.collection("attendance").document(docId).set(rec);
            } catch (Exception ignored) {}
        }
    }

    public List<Map<String, Object>> getStudentHistory(String uid) {
        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();

        Firestore db = getDb();
        if (db != null) {
            try {
                ApiFuture<QuerySnapshot> future = db.collection("attendance").whereEqualTo("uid", uid).get();
                List<? extends DocumentSnapshot> docs = future.get().getDocuments();
                for (DocumentSnapshot d : docs) {
                    if (d.getData() != null) {
                        list.add(new HashMap<>(d.getData()));
                        addedIds.add(d.getId());
                    }
                }
            } catch (Exception ignored) {}
        }

        for (Map.Entry<String, Map<String, Object>> entry : attendanceLedger.entrySet()) {
            if (uid.equals(entry.getValue().get("uid")) && !addedIds.contains(entry.getKey())) {
                list.add(new HashMap<>(entry.getValue()));
            }
        }

        // Sort descending by date
        list.sort((a, b) -> String.valueOf(b.getOrDefault("date", "")).compareTo(String.valueOf(a.getOrDefault("date", ""))));
        return list;
    }

    private void initDefaultHistoricalRecords(String uid) {
        LocalDate today = LocalDate.now(applicationZoneId);
        long now = System.currentTimeMillis();

        // Populate 3 days of prior working attendance for realistic analytics
        String d1 = today.minusDays(1).format(DATE_FORMATTER);
        String id1 = uid + "_" + d1;
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", id1); r1.put("uid", uid); r1.put("date", d1); r1.put("status", "present"); r1.put("respondedAt", now - 86400000L);
        attendanceLedger.put(id1, r1);

        String d2 = today.minusDays(2).format(DATE_FORMATTER);
        String id2 = uid + "_" + d2;
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", id2); r2.put("uid", uid); r2.put("date", d2); r2.put("status", "excused_meeting"); r2.put("respondedAt", now - 172800000L);
        attendanceLedger.put(id2, r2);

        String d3 = today.minusDays(3).format(DATE_FORMATTER);
        String id3 = uid + "_" + d3;
        Map<String, Object> r3 = new HashMap<>();
        r3.put("id", id3); r3.put("uid", uid); r3.put("date", d3); r3.put("status", "present"); r3.put("respondedAt", now - 259200000L);
        attendanceLedger.put(id3, r3);
    }
}
