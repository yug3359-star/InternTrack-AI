package com.interntrack.scheduler;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.DocumentSnapshot;
import com.interntrack.service.DailyStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Arrays;

/**
 * Scheduled automated engagement monitor generating random working-hour attendance check-ins.
 * Enforces strict compliance by verifying 5 random pop-ups per work shift (excluding lunch breaks).
 * State is stored persistently in Firestore to survive server reboots and deployments.
 */
@Component
public class EngagementPopupJob {

    private static final Logger log = LoggerFactory.getLogger(EngagementPopupJob.class);

    private final ZoneId applicationZoneId;
    private final Random random = new Random();

    @Autowired(required = false)
    private DailyStatusService dailyStatusService;

    public EngagementPopupJob(ZoneId applicationZoneId) {
        this.applicationZoneId = applicationZoneId;
    }

    /**
     * Daily initializer cron job executing at midnight to provision 5 random timestamps
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void generateDailyEngagementSchedules() {
        log.info("Executing daily engagement popup schedule generation for Ongoing students.");
        LocalDate today = LocalDate.now(applicationZoneId);
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentDayName = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Map<String, DocumentSnapshot> ongoingDocs = new HashMap<>();
                try {
                    ApiFuture<QuerySnapshot> future = db.collection("internships").whereEqualTo("status", "Ongoing").get();
                    for (QueryDocumentSnapshot d : future.get().getDocuments()) {
                        ongoingDocs.put(d.getId(), d);
                    }
                } catch (Exception e) {
                    log.warn("Could not query internships, using defaults.");
                }

                if (!ongoingDocs.containsKey("dev-stud-107")) ongoingDocs.put("dev-stud-107", null);
                if (!ongoingDocs.containsKey("dev-stud-102")) ongoingDocs.put("dev-stud-102", null);

                for (Map.Entry<String, DocumentSnapshot> entry : ongoingDocs.entrySet()) {
                    String uid = entry.getKey();
                    DocumentSnapshot d = entry.getValue();

                    if (d != null && d.contains("workingDays")) {
                        List<String> workingDays = (List<String>) d.get("workingDays");
                        if (workingDays != null && !workingDays.isEmpty() && !workingDays.contains(currentDayName)) {
                            log.info("Skipping popup schedule for student [{}] because today ({}) is a rest day.", uid, currentDayName);
                            continue;
                        }
                    }

                    String start = (d != null && d.getString("officeStartTime") != null) ? d.getString("officeStartTime") : (uid.equals("dev-stud-102") ? "08:30" : "09:00");
                    String end = (d != null && d.getString("officeEndTime") != null) ? d.getString("officeEndTime") : (uid.equals("dev-stud-102") ? "16:30" : "17:00");
                    String bStart = (d != null && d.getString("breakStartTime") != null) ? d.getString("breakStartTime") : (uid.equals("dev-stud-102") ? "12:30" : "13:00");
                    String bEnd = (d != null && d.getString("breakEndTime") != null) ? d.getString("breakEndTime") : (uid.equals("dev-stud-102") ? "13:30" : "14:00");

                    List<LocalTime> schedule = computeRandomWorkingTimestamps(start, end, bStart, bEnd);
                    List<String> scheduleStrings = new ArrayList<>();
                    for (LocalTime t : schedule) scheduleStrings.add(t.toString());

                    Map<String, Object> docData = new HashMap<>();
                    docData.put("uid", uid);
                    docData.put("date", todayStr);
                    docData.put("scheduledPopups", scheduleStrings);
                    docData.put("shiftEndTime", end);
                    docData.put("missedCount", 0);
                    docData.put("auditCompleted", false);

                    db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).set(docData);
                }
                log.info("Daily engagement schedules initialized successfully in Firestore.");
            }
        } catch (Exception e) {
            log.error("Failed to generate daily schedules: {}", e.getMessage());
        }
    }

    /**
     * Periodic evaluation cron running every 1 minute 24/7 to verify if scheduled timestamps have arrived.
     * Also evaluates shift-end absences dynamically.
     */
    @Scheduled(cron = "0 * * * * *")
    public void dispatchScheduledPopups() {
        LocalTime now = LocalTime.now(applicationZoneId).truncatedTo(ChronoUnit.MINUTES);
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db == null) return;

            ApiFuture<QuerySnapshot> future = db.collection("daily_engagement_schedules")
                .whereEqualTo("date", todayStr)
                .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                String uid = doc.getString("uid");
                List<String> scheduledPopups = (List<String>) doc.get("scheduledPopups");
                String shiftEndStr = doc.getString("shiftEndTime");
                Boolean auditCompleted = doc.getBoolean("auditCompleted");
                if (auditCompleted == null) auditCompleted = false;

                if (scheduledPopups != null && scheduledPopups.contains(now.toString())) {
                    log.info("Engagement audit triggered! Dispatching popup for student [{}] at time [{}]", uid, now);
                    emitPendingPopupToStore(uid, now);
                }

                if (!auditCompleted) {
                    Long missedCount = doc.getLong("missedCount");
                    if (missedCount == null) missedCount = 0L;
                    
                    if (missedCount >= 3) {
                        log.warn("Student [{}] accrued [{}] missed check-ins. Stamp ABSENCE!", uid, missedCount);
                        recordDailyAbsencePenalty(uid, missedCount.intValue());
                        doc.getReference().update("auditCompleted", true);
                    } else if (shiftEndStr != null) {
                        LocalTime shiftEnd = LocalTime.parse(shiftEndStr);
                        // Delay audit completion by 5 minutes after shift end to ensure final popups expire
                        if (now.isAfter(shiftEnd.plusMinutes(5))) {
                            doc.getReference().update("auditCompleted", true);
                        }
                    }
                }
            }
        } catch (Exception e) {}
    }

    public List<LocalTime> computeRandomWorkingTimestamps(String startStr, String endStr, String breakStartStr, String breakEndStr) {
        LocalTime start = LocalTime.parse(startStr);
        LocalTime end = LocalTime.parse(endStr);
        LocalTime bStart = LocalTime.parse(breakStartStr);
        LocalTime bEnd = LocalTime.parse(breakEndStr);

        int totalStartMin = start.getHour() * 60 + start.getMinute();
        int totalEndMin = end.getHour() * 60 + end.getMinute();
        int bStartMin = bStart.getHour() * 60 + bStart.getMinute();
        int bEndMin = bEnd.getHour() * 60 + bEnd.getMinute();

        List<Integer> validMinutes = new ArrayList<>();
        for (int m = totalStartMin; m <= totalEndMin - 30; m++) {
            if (m < bStartMin || m > bEndMin) {
                validMinutes.add(m);
            }
        }

        Collections.shuffle(validMinutes, random);
        List<LocalTime> scheduledTimes = new ArrayList<>();
        int targetCount = Math.min(5, validMinutes.size());
        
        for (int i = 0; i < targetCount; i++) {
            int mins = validMinutes.get(i);
            scheduledTimes.add(LocalTime.of(mins / 60, mins % 60));
        }

        Collections.sort(scheduledTimes);
        return scheduledTimes;
    }

    public void incrementMissedPopup(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("daily_engagement_schedules").document(uid + "_" + todayStr)
                  .update("missedCount", com.google.cloud.firestore.FieldValue.increment(1));
                log.warn("Student [{}] missed popup check-in. Incremented daily missed count in Firestore.", uid);
            }
        } catch (Exception e) {}
    }
    
    public int getMissedCountToday(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                var docSnapshot = db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).get().get();
                if (docSnapshot.exists()) {
                    Long missedCount = docSnapshot.getLong("missedCount");
                    return missedCount != null ? missedCount.intValue() : 0;
                }
            }
        } catch (Exception e) {}
        return 0;
    }

    public void clearPopupsForToday(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("daily_engagement_schedules").document(uid + "_" + todayStr)
                  .update("scheduledPopups", Collections.emptyList());
                log.info("Cleared all scheduled random engagement popups for today for student [{}] in Firestore", uid);
            }
        } catch (Exception e) {}
    }

    public Map<String, Object> getStudentEngagementStatus(String uid) {
        Map<String, Object> status = new HashMap<>();
        status.put("uid", uid);
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                var docSnapshot = db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).get().get();
                if (docSnapshot.exists()) {
                    List<String> scheduledPopups = (List<String>) docSnapshot.get("scheduledPopups");
                    Long missedToday = docSnapshot.getLong("missedCount");
                    
                    int totalScheduled = scheduledPopups != null ? scheduledPopups.size() : 0;
                    int popupsElapsed = 0;
                    LocalTime now = LocalTime.now(applicationZoneId);
                    if (scheduledPopups != null) {
                        for (String tStr : scheduledPopups) {
                            LocalTime t = LocalTime.parse(tStr);
                            if (now.isAfter(t) || now.equals(t)) popupsElapsed++;
                        }
                    }
                    
                    status.put("totalPopupsScheduled", totalScheduled);
                    status.put("popupsElapsed", popupsElapsed);
                    status.put("popupsRemaining", totalScheduled - popupsElapsed);
                    status.put("missedToday", missedToday != null ? missedToday.intValue() : 0);
                    status.put("absenceWarningThreshold", 3);
                    return status;
                }
            }
        } catch (Exception e) {}
        
        // Fallback
        status.put("totalPopupsScheduled", 5);
        status.put("popupsElapsed", 0);
        status.put("popupsRemaining", 5);
        status.put("missedToday", 0);
        status.put("absenceWarningThreshold", 3);
        return status;
    }

    private void emitPendingPopupToStore(String uid, LocalTime scheduledTime) {
        long now = System.currentTimeMillis();
        long deadline = now + 120000L; // 2 minute response window
        String popupId = uid + "_popup_" + now;

        Map<String, Object> docData = new HashMap<>();
        docData.put("id", popupId);
        docData.put("uid", uid);
        docData.put("timestamp", now);
        docData.put("deadline", deadline);
        docData.put("scheduledTime", scheduledTime.toString());
        docData.put("status", "PENDING");

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("popups").document(uid).collection("pending").document(popupId).set(docData);
            }
        } catch (Exception ignored) {}
    }

    public void resolvePendingPopup(String uid, String popupId) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("popups").document(uid).collection("pending").document(popupId).update("status", "COMPLETED");
            }
        } catch (Exception e) {}
    }

    /**
     * Cleanup job running every 30 seconds to sweep for unanswered popups past their 2-minute deadline.
     */
    @Scheduled(fixedRate = 30000)
    public void sweepExpiredPopups() {
        long now = System.currentTimeMillis();
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db == null) return;
            
            ApiFuture<QuerySnapshot> future = db.collection("daily_engagement_schedules")
                .whereEqualTo("date", todayStr)
                .get();

            for (QueryDocumentSnapshot scheduleDoc : future.get().getDocuments()) {
                String uid = scheduleDoc.getString("uid");
                if (uid == null) continue;
                
                ApiFuture<QuerySnapshot> pendingFuture = db.collection("popups").document(uid).collection("pending")
                    .whereEqualTo("status", "PENDING")
                    .get();
                
                for (QueryDocumentSnapshot pendingDoc : pendingFuture.get().getDocuments()) {
                    Long deadline = pendingDoc.getLong("deadline");
                    if (deadline != null && now > deadline) {
                        String popupId = pendingDoc.getId();
                        log.warn("Popup [{}] expired unanswered for student [{}].", popupId, uid);
                        incrementMissedPopup(uid);
                        pendingDoc.getReference().update("status", "MISSED");
                    }
                }
            }
        } catch (Exception e) {}
    }

    private void recordDailyAbsencePenalty(String uid, int missedCount) {
        if (dailyStatusService != null) {
            dailyStatusService.evaluateDailyStatus(uid, LocalDate.now(applicationZoneId), missedCount);
        }
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Map<String, Object> penalty = new HashMap<>();
                penalty.put("uid", uid);
                penalty.put("reason", "Missed " + missedCount + " daily working-hour pop-up check-ins (Threshold: >=3)");
                penalty.put("date", LocalTime.now(applicationZoneId).toString());
                penalty.put("penaltyType", "DAILY_ABSENCE");
                db.collection("compliance_penalties").add(penalty);
            }
        } catch (Exception ignored) {}
    }
}
