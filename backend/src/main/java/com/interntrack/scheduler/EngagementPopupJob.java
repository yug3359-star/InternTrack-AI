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
import java.util.Set;
import java.util.HashSet;

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
     * Periodic cron job executing hourly to provision 5 random timestamps for Ongoing students.
     * Crucial: It skips students who already have a schedule for today to prevent overwriting.
     */
    @Scheduled(cron = "0 * * * * *")
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

                // Removed dev mock fallbacks

                // Fetch existing schedules for today to avoid overwriting and resetting missed counts
                Set<String> existingUids = new HashSet<>();
                try {
                    ApiFuture<QuerySnapshot> existingFuture = db.collection("daily_engagement_schedules").whereEqualTo("date", todayStr).get();
                    for (QueryDocumentSnapshot doc : existingFuture.get().getDocuments()) {
                        existingUids.add(doc.getString("uid"));
                    }
                } catch (Exception e) {
                    log.warn("Could not query existing schedules.");
                }

                for (Map.Entry<String, DocumentSnapshot> entry : ongoingDocs.entrySet()) {
                    String uid = entry.getKey();
                    if (existingUids.contains(uid)) {
                        continue; // Schedule already generated for today
                    }
                    DocumentSnapshot d = entry.getValue();

                    if (d != null && d.contains("workingDays")) {
                        List<String> workingDays = (List<String>) d.get("workingDays");
                        if (workingDays != null && !workingDays.isEmpty() && !workingDays.contains(currentDayName)) {
                            log.info("Skipping popup schedule for student [{}] because today ({}) is a rest day.", uid, currentDayName);
                            continue;
                        }
                    }

                    String start = (d != null && d.getString("officeStartTime") != null) ? d.getString("officeStartTime") : "09:00";
                    String end = (d != null && d.getString("officeEndTime") != null) ? d.getString("officeEndTime") : "17:00";
                    String bStart = (d != null && d.getString("breakStartTime") != null) ? d.getString("breakStartTime") : "13:00";
                    String bEnd = (d != null && d.getString("breakEndTime") != null) ? d.getString("breakEndTime") : "14:00";

                    List<LocalTime> schedule = computeRandomWorkingTimestamps(start, end, bStart, bEnd);
                    List<String> scheduleStrings = new ArrayList<>();
                    for (LocalTime t : schedule) scheduleStrings.add(t.toString());

                    Map<String, Object> docData = new HashMap<>();
                    docData.put("uid", uid);
                    docData.put("date", todayStr);
                    docData.put("scheduledPopups", scheduleStrings);
                    if (!scheduleStrings.isEmpty()) {
                        docData.put("biometricPopupTime", scheduleStrings.get(random.nextInt(scheduleStrings.size())));
                    }
                    docData.put("shiftEndTime", end);
                    docData.put("missedCount", 0);
                    docData.put("completedCount", 0);
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
     * Generates on-demand popup schedule for a specific student, immediately bypassing the hourly cron.
     */
    public void generateScheduleForStudent(String uid) {
        LocalDate today = LocalDate.now(applicationZoneId);
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentDayName = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db == null) return;
            
            // Avoid overwriting existing schedules for today
            DocumentSnapshot existing = db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).get().get();
            if (existing.exists()) {
                log.info("Schedule already exists for [{}]. Skipping on-demand generation.", uid);
                return;
            }

            DocumentSnapshot d = db.collection("internships").document(uid).get().get();
            if (!d.exists() || !"Ongoing".equals(d.getString("status"))) {
                return;
            }

            if (d.contains("workingDays")) {
                List<String> workingDays = (List<String>) d.get("workingDays");
                if (workingDays != null && !workingDays.isEmpty() && !workingDays.contains(currentDayName)) {
                    log.info("Skipping popup schedule for student [{}] because today ({}) is a rest day.", uid, currentDayName);
                    return;
                }
            }

            String start = d.getString("officeStartTime") != null ? d.getString("officeStartTime") : "09:00";
            String end = d.getString("officeEndTime") != null ? d.getString("officeEndTime") : "17:00";
            String bStart = d.getString("breakStartTime") != null ? d.getString("breakStartTime") : "13:00";
            String bEnd = d.getString("breakEndTime") != null ? d.getString("breakEndTime") : "14:00";

            List<LocalTime> schedule = computeRandomWorkingTimestamps(start, end, bStart, bEnd);
            List<String> scheduleStrings = new ArrayList<>();
            for (LocalTime t : schedule) scheduleStrings.add(t.toString());

            Map<String, Object> docData = new HashMap<>();
            docData.put("uid", uid);
            docData.put("date", todayStr);
            docData.put("scheduledPopups", scheduleStrings);
            if (!scheduleStrings.isEmpty()) {
                docData.put("biometricPopupTime", scheduleStrings.get(random.nextInt(scheduleStrings.size())));
            }
            docData.put("shiftEndTime", end);
            docData.put("missedCount", 0);
            docData.put("completedCount", 0);
            docData.put("auditCompleted", false);

            db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).set(docData);
            log.info("Successfully generated on-demand popup schedule for student [{}]", uid);
        } catch (Exception e) {
            log.error("Failed to generate on-demand popup schedule for student [{}]: {}", uid, e.getMessage());
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

                String biometricTime = doc.getString("biometricPopupTime");

                if (scheduledPopups != null && scheduledPopups.contains(now.toString())) {
                    boolean requiresBiometric = now.toString().equals(biometricTime);
                    log.info("Engagement audit triggered! Dispatching popup for student [{}] at time [{}]. Biometric required: {}", uid, now, requiresBiometric);
                    emitPendingPopupToStore(uid, now, requiresBiometric);
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

        if (totalEndMin < totalStartMin) totalEndMin += 1440;
        if (bStartMin < totalStartMin) bStartMin += 1440;
        if (bEndMin < bStartMin) bEndMin += 1440;

        LocalTime now = LocalTime.now(applicationZoneId);
        int nowMin = now.getHour() * 60 + now.getMinute();
        
        // Normalize nowMin for early morning hours if shift spans midnight
        if (totalEndMin >= 1440 && nowMin < 720) {
            nowMin += 1440;
        }

        List<Integer> validMinutes = new ArrayList<>();
        for (int m = totalStartMin; m <= totalEndMin - 30; m++) {
            if ((m < bStartMin || m > bEndMin) && m > nowMin) {
                validMinutes.add(m);
            }
        }

        Collections.shuffle(validMinutes, random);
        List<LocalTime> scheduledTimes = new ArrayList<>();
        int targetCount = Math.min(5, validMinutes.size());
        
        for (int i = 0; i < targetCount; i++) {
            int mins = validMinutes.get(i);
            int actualMins = mins % 1440;
            scheduledTimes.add(LocalTime.of(actualMins / 60, actualMins % 60));
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
                  .update("missedCount", com.google.cloud.firestore.FieldValue.increment(1)).get();
                log.warn("Student [{}] missed popup check-in. Incremented daily missed count in Firestore.", uid);
            }
        } catch (Exception e) {
            log.error("Failed to increment missed popup count for {}: {}", uid, e.getMessage());
        }
    }
    
    public void incrementCompletedPopup(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("daily_engagement_schedules").document(uid + "_" + todayStr)
                  .update("completedCount", com.google.cloud.firestore.FieldValue.increment(1)).get();
                log.info("Student [{}] successfully completed popup check-in. Incremented daily completed count.", uid);
            }
        } catch (Exception e) {
            log.error("Failed to increment completed popup count for {}: {}", uid, e.getMessage());
        }
    }
    
    public int getMissedCountToday(String uid) {
        String todayStr = LocalDate.now(applicationZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                DocumentSnapshot docSnapshot = db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).get().get();
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
                DocumentSnapshot docSnapshot = db.collection("daily_engagement_schedules").document(uid + "_" + todayStr).get().get();
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
                    Long completedCount = docSnapshot.getLong("completedCount");
                    status.put("completedToday", completedCount != null ? completedCount.intValue() : 0);
                    status.put("absenceWarningThreshold", 3);

                    // Piggyback any active pending popup for the frontend (bypasses Firestore Security Rules blocking)
                    java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> pendingDocs = db.collection("popups").document(uid).collection("pending")
                            .whereEqualTo("status", "PENDING").get().get().getDocuments();
                    if (!pendingDocs.isEmpty()) {
                        Map<String, Object> popupData = new HashMap<>(pendingDocs.get(0).getData());
                        popupData.put("id", pendingDocs.get(0).getId());
                        status.put("activePopup", popupData);
                    }

                    return status;
                }
            }
        } catch (Exception e) {}
        
        // Fallback
        status.put("totalPopupsScheduled", 5);
        status.put("popupsElapsed", 0);
        status.put("popupsRemaining", 5);
        status.put("missedToday", 0);
        status.put("completedToday", 0);
        status.put("absenceWarningThreshold", 3);
        return status;
    }

    private void emitPendingPopupToStore(String uid, LocalTime scheduledTime, boolean requiresBiometric) {
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
        docData.put("requiresBiometric", requiresBiometric);
        docData.put("message", requiresBiometric ? "Random engagement check-in. Biometric verification required." : "Standard engagement check-in. Tap to verify attendance.");

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("popups").document(uid).collection("pending").document(popupId).set(docData);
            }
        } catch (Exception ignored) {}
    }

    public boolean resolvePendingPopup(String uid, String popupId) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                com.google.cloud.firestore.DocumentReference docRef = db.collection("popups").document(uid).collection("pending").document(popupId);
                return db.runTransaction(t -> {
                    com.google.cloud.firestore.DocumentSnapshot snapshot = t.get(docRef).get();
                    if (snapshot.exists() && "PENDING".equals(snapshot.getString("status"))) {
                        t.update(docRef, "status", "COMPLETED");
                        return true;
                    }
                    return false;
                }).get();
            }
        } catch (Exception e) {
            log.error("Failed to resolve pending popup: {}", e.getMessage());
        }
        return false;
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
                        pendingDoc.getReference().update("status", "MISSED").get();
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
