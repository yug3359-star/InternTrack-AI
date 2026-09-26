package com.interntrack.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared Daily Compliance & Status Service (Module 5a & Module 5b).
 * Unifies attendance and unscheduled engagement signals into authoritative daily status records
 * persisted in real Firestore at 'daily_status/{uid}_{yyyy-MM-dd}'.
 * Rule: 3+ missed pop-ups OR one missed formal attendance = absent for that day.
 */
@Service
public class DailyStatusService {

    private static final Logger log = LoggerFactory.getLogger(DailyStatusService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired(required = false)
    private Firestore firestore;

    private final ZoneId applicationZoneId = ZoneId.of("Asia/Kolkata");
    private final Map<String, Map<String, Object>> localDailyStatusLedger = new ConcurrentHashMap<>();

    private Firestore getDb() {
        if (this.firestore != null) return this.firestore;
        try {
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Evaluates daily status using real Firestore records for attendance and engagement missed counts.
     */
    public Map<String, Object> evaluateDailyStatus(String uid, LocalDate date, int missedPopupsCount) {
        String dateStr = date.format(DATE_FORMATTER);
        String docId = uid + "_" + dateStr;

        Firestore db = getDb();
        String attendanceStatus = "pending";
        
        // Step 1: Read formal attendance/{uid}_{date} signal
        if (db != null) {
            try {
                DocumentSnapshot attDoc = db.collection("attendance").document(docId).get().get();
                if (attDoc.exists() && attDoc.getString("status") != null) {
                    attendanceStatus = attDoc.getString("status");
                }
            } catch (Exception e) {
                log.warn("Could not query formal attendance doc [{}] during daily evaluation: {}", docId, e.getMessage());
            }
        }

        // Step 1.5: Read Diary Signal
        boolean diaryRejected = false;
        boolean diaryAccepted = false;
        boolean forcedDiaryAbsence = false;
        
        if (db != null) {
            try {
                DocumentSnapshot s = db.collection("suspicious_diaries").document(docId).get().get();
                if (s.exists()) {
                    diaryRejected = true;
                } else {
                    DocumentSnapshot d = db.collection("diaries").document(docId).get().get();
                    if (d.exists() && "accepted".equalsIgnoreCase(d.getString("status"))) {
                        diaryAccepted = true;
                    }
                }
                
                DocumentSnapshot currentStatus = db.collection("daily_status").document(docId).get().get();
                if (currentStatus.exists()) {
                    if ("absent".equalsIgnoreCase(currentStatus.getString("status"))) {
                        String existingReason = currentStatus.getString("absenceReason");
                        if (existingReason != null && existingReason.contains("Failed to submit mandatory daily work diary")) {
                            forcedDiaryAbsence = true;
                        }
                    }
                    if (missedPopupsCount == 0 && currentStatus.contains("missedPopupsCount")) {
                        Long existingPopups = currentStatus.getLong("missedPopupsCount");
                        if (existingPopups != null) {
                            missedPopupsCount = existingPopups.intValue();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Step 2: Evaluate unified compliance rules
        boolean isAbsent = false;
        String absenceReason = null;

        if ("missed".equalsIgnoreCase(attendanceStatus)) {
            isAbsent = true;
            absenceReason = "Missed deliberate formal daily attendance check-in window without valid meeting quota pass.";
        } else if (missedPopupsCount >= 3) {
            isAbsent = true;
            absenceReason = "Accrued " + missedPopupsCount + " unanswered working hour engagement popups today (threshold >= 3).";
        } else if (diaryRejected) {
            isAbsent = true;
            absenceReason = "Daily work diary was rejected by AI verification.";
        } else if (forcedDiaryAbsence) {
            isAbsent = true;
            absenceReason = "Failed to submit mandatory daily work diary.";
        }

        String finalStatus;
        if (isAbsent) {
            finalStatus = "absent";
        } else {
            // To be completely present, ALL THREE MUST MATCH
            if ("excused_meeting".equalsIgnoreCase(attendanceStatus) && diaryAccepted) {
                finalStatus = "excused_meeting";
            } else if ("present".equalsIgnoreCase(attendanceStatus) && diaryAccepted) {
                finalStatus = "present";
            } else {
                finalStatus = "in_progress";
            }
        }

        Map<String, Object> record = new HashMap<>();
        record.put("uid", uid);
        record.put("date", dateStr);
        record.put("status", finalStatus);
        record.put("missedPopupsCount", missedPopupsCount);
        record.put("attendanceStatus", attendanceStatus);
        record.put("evaluatedAt", System.currentTimeMillis());
        if (absenceReason != null) {
            record.put("absenceReason", absenceReason);
        }

        localDailyStatusLedger.put(docId, record);

        // Step 3: Write authoritative record to real Firestore
        if (db != null) {
            try {
                db.collection("daily_status").document(docId).set(record);
                if (isAbsent) {
                    try {
                        DocumentSnapshot existingAtt = db.collection("attendance").document(docId).get().get();
                        if (!existingAtt.exists()) {
                            // Synthesize a missing attendance document so it appears on the dashboard audit table
                            Map<String, Object> newAtt = new HashMap<>();
                            newAtt.put("id", docId);
                            newAtt.put("uid", uid);
                            newAtt.put("date", dateStr);
                            newAtt.put("status", "missed"); // Baseline is missed since they never checked in
                            if (absenceReason != null) {
                                newAtt.put("absenceReason", absenceReason);
                            }
                            db.collection("attendance").document(docId).set(newAtt);
                        }
                    } catch (Exception ignored) {}
                }
                log.info("Committed daily compliance record to real Firestore: daily_status/{} -> [{}]", docId, finalStatus.toUpperCase());
            } catch (Exception e) {
                log.error("Failed to commit daily status to cloud Firestore for doc {}: {}", docId, e.getMessage());
            }
        } else {
            log.warn("Offline fallback mode: Recorded daily compliance status for doc {} as [{}]", docId, finalStatus);
        }

        return record;
    }

    /**
     * Overloaded method when evaluating simply upon formal attendance state transitions
     */
    public Map<String, Object> updateFromAttendanceTransition(String uid, LocalDate date) {
        return evaluateDailyStatus(uid, date, 0);
    }

    public Map<String, Object> getStudentDailyStatus(String uid, LocalDate date) {
        String docId = uid + "_" + date.format(DATE_FORMATTER);
        Firestore db = getDb();
        if (db != null) {
            try {
                DocumentSnapshot doc = db.collection("daily_status").document(docId).get().get();
                if (doc.exists() && doc.getData() != null) {
                    return new HashMap<>(doc.getData());
                }
            } catch (Exception e) {
                log.warn("Offline reading daily status for doc [{}]: {}", docId, e.getMessage());
            }
        }
        return localDailyStatusLedger.getOrDefault(docId, Collections.emptyMap());
    }

    /**
     * Helper method to record specific compliance evaluation results or penalties from external modules (Module 5c Tests).
     */
    public void recordAttendanceResult(String uid, String dateStr, String status, String reason) {
        String docId = uid + "_" + dateStr;
        Map<String, Object> record = new HashMap<>(localDailyStatusLedger.getOrDefault(docId, new HashMap<>()));
        record.put("uid", uid);
        record.put("date", dateStr);
        record.put("status", "absent".equalsIgnoreCase(status) || "missed".equalsIgnoreCase(status) ? "absent" : status);
        record.put("absenceReason", reason);
        record.put("updatedAt", System.currentTimeMillis());

        localDailyStatusLedger.put(docId, record);
        Firestore db = getDb();
        if (db != null) {
            try {
                db.collection("daily_status").document(docId).set(record);
                log.info("Recorded Module 5c compliance evaluation to daily_status/{}: {}", docId, reason);
            } catch (Exception e) {
                log.error("Could not write compliance penalty to daily_status/{}: {}", docId, e.getMessage());
            }
        }
    }
}
