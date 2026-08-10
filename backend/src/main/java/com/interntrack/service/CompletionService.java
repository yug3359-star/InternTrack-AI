package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative backend evaluation service executing internship completion transitions
 * and generating permanent compliance certification ledgers in Firestore.
 */
@Service
public class CompletionService {

    private static final Logger log = LoggerFactory.getLogger(CompletionService.class);

    private final ZoneId applicationZoneId;
    private final Map<String, Map<String, Object>> simulatedCompletionRegistry = new ConcurrentHashMap<>();

    public CompletionService(ZoneId applicationZoneId) {
        this.applicationZoneId = applicationZoneId;
        initializeMockCompletionRecords();
    }

    /**
     * Identifies all internships currently in "Ongoing" status where completionDate <= server today.
     * Transitions status to "Completed", sets completedAt, computes real metrics across historical
     * collections, and permanently saves the report in completion_summaries/{uid}.
     *
     * @return total count of candidate records transitioned to Completed status
     */
    public int runCompletionTransitionCheck() {
        LocalDate today = LocalDate.now(applicationZoneId);
        long nowMs = System.currentTimeMillis();
        int completedCount = 0;

        log.info("Starting automated internship completion audit against server date [{}] (Timezone: [{}])",
                today, applicationZoneId.getId());

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                ApiFuture<QuerySnapshot> future = db.collection("internships")
                        .whereEqualTo("status", "Ongoing")
                        .get();
                List<? extends DocumentSnapshot> ongoingDocs = future.get().getDocuments();

                for (DocumentSnapshot doc : ongoingDocs) {
                    String uid = doc.getId();
                    String completionDateStr = doc.getString("completionDate");
                    String joiningDateStr = doc.getString("joiningDate");

                    if (completionDateStr != null && !completionDateStr.trim().isEmpty()) {
                        try {
                            LocalDate compDate = LocalDate.parse(completionDateStr.trim());
                            if (!compDate.isAfter(today)) {
                                log.info("Candidate [{}] completionDate [{}] <= serverDate [{}]. Initiating final compliance aggregation...",
                                        uid, completionDateStr, today);

                                // 1. Transition internship status to Completed
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("status", "Completed");
                                updates.put("completedAt", nowMs);
                                db.collection("internships").document(uid).update(updates).get();

                                // 2. Calculate real compliance metrics across historical collections
                                Map<String, Object> summary = computeRealComplianceSummary(db, uid, joiningDateStr, completionDateStr, doc, nowMs);

                                // 3. Permanently save record to completion_summaries collection
                                db.collection("completion_summaries").document(uid).set(summary, SetOptions.merge()).get();

                                log.info("Successfully persisted permanent completion summary report for candidate [{}].", uid);
                                completedCount++;
                            }
                        } catch (DateTimeParseException e) {
                            log.warn("Completion audit rejected: Malformed completionDate string [{}] for record ID [{}]", completionDateStr, uid);
                        }
                    } else {
                        log.warn("Completion audit warning: Record ID [{}] is Ongoing but lacks completionDate field", uid);
                    }
                }
            }
        } catch (IllegalStateException | NoClassDefFoundError e) {
            log.warn("Firebase runtime uninitialized during completion evaluation. Evaluating dev simulation records.");
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("default FirebaseApp is not initialized") || e.getMessage().contains("has not been initialized"))) {
                log.warn("Cloud Firestore unreachable during completion check. Proceeding with offline simulation evaluation.");
            } else {
                log.error("Error executing completion transition check on cloud database: {}", e.getMessage(), e);
            }
        }

        // Simultaneously transition Dev simulation candidates whose completion Date has passed
        for (Map<String, Object> mock : simulatedCompletionRegistry.values()) {
            if (!Boolean.TRUE.equals(mock.get("isFinalized")) && mock.get("completionDate") != null) {
                try {
                    LocalDate mockCompDate = LocalDate.parse(String.valueOf(mock.get("completionDate")));
                    if (!mockCompDate.isAfter(today)) {
                        mock.put("isFinalized", true);
                        mock.put("status", "Completed");
                        mock.put("completedAt", nowMs);
                        log.info("Audit Dev Simulation Transition: Candidate [{}] transitioned to Completed status.", mock.get("uid"));
                        completedCount++;
                    }
                } catch (Exception ignored) {}
            }
        }

        log.info("Completed automated completion transition cycle. Total records completed: [{}]", completedCount);
        return completedCount;
    }

    /**
     * Computes real compliance metrics by querying attendance, diaries, tests, and warnings.
     * Enforces strict division-by-zero safeguards across all fractional percentage operations.
     */
    private Map<String, Object> computeRealComplianceSummary(Firestore db, String uid, String joinStr, String compStr, DocumentSnapshot internDoc, long nowMs) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("uid", uid);
        summary.put("joiningDate", joinStr);
        summary.put("completionDate", compStr);
        summary.put("completedAt", nowMs);
        
        // Populate identity metadata from internship document
        if (internDoc != null) {
            summary.put("studentName", internDoc.getString("studentName"));
            summary.put("branch", internDoc.getString("branch"));
            summary.put("mentorName", internDoc.getString("mentorName"));
            summary.put("internshipDomain", internDoc.getString("internshipDomain"));
        }

        // 1. Calculate Expected Working Days between joiningDate and completionDate (excluding weekends)
        long expectedWorkingDays = 0;
        try {
            if (joinStr != null && compStr != null) {
                LocalDate start = LocalDate.parse(joinStr.trim());
                LocalDate end = LocalDate.parse(compStr.trim());
                LocalDate curr = start;
                while (!curr.isAfter(end)) {
                    if (curr.getDayOfWeek() != DayOfWeek.SATURDAY && curr.getDayOfWeek() != DayOfWeek.SUNDAY) {
                        expectedWorkingDays++;
                    }
                    curr = curr.plusDays(1);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse expected working dates for UID [{}]: {}", uid, e.getMessage());
        }
        if (expectedWorkingDays <= 0) {
            expectedWorkingDays = 60; // Default institutional semester duration fallback for division guard
        }
        summary.put("expectedWorkingDays", expectedWorkingDays);

        // 2. Query Real Attendance and Excuse Quota Usage
        long presentOrExcusedCount = 0;
        long totalExcuseCount = 0;
        long totalEvaluationsCount = 0;

        try {
            QuerySnapshot statusSnap = db.collection("daily_status").whereEqualTo("uid", uid).get().get();
            for (DocumentSnapshot sDoc : statusSnap.getDocuments()) {
                String st = sDoc.getString("status");
                totalEvaluationsCount++;
                if ("present".equalsIgnoreCase(st) || "excused".equalsIgnoreCase(st) || "excused_meeting".equalsIgnoreCase(st) || "APPROVED".equalsIgnoreCase(st)) {
                    presentOrExcusedCount++;
                }
                if ("excused".equalsIgnoreCase(st) || "excused_meeting".equalsIgnoreCase(st)) {
                    totalExcuseCount++;
                }
            }
        } catch (Exception e) {
            log.warn("Error querying attendance/daily_status for candidate [{}]: {}", uid, e.getMessage());
        }

        // Division-by-Zero Guard for Attendance Percentage
        double attendancePercent = (expectedWorkingDays > 0) 
                ? Math.min(100.0, ((double) presentOrExcusedCount / (double) expectedWorkingDays) * 100.0) 
                : 0.0;
        summary.put("attendancePercentage", Math.round(attendancePercent * 10.0) / 10.0);
        summary.put("verifiedAttendanceDays", presentOrExcusedCount);

        // 3. Query Real Diary Compliance
        long acceptedDiaries = 0;
        try {
            QuerySnapshot diarySnap = db.collection("diaries").whereEqualTo("uid", uid).get().get();
            for (DocumentSnapshot dDoc : diarySnap.getDocuments()) {
                String dSt = dDoc.getString("status");
                if ("accepted".equalsIgnoreCase(dSt) || "approved".equalsIgnoreCase(dSt) || "Approved".equals(dSt)) {
                    acceptedDiaries++;
                }
            }
        } catch (Exception e) {
            log.warn("Error querying diary records for candidate [{}]: {}", uid, e.getMessage());
        }

        summary.put("acceptedDiariesCount", acceptedDiaries);

        // 4. Query Real Test History & Scores
        long completedTests = 0;
        long absentTests = 0;
        double totalTestScore = 0.0;

        try {
            QuerySnapshot testSnap = db.collection("tests").whereEqualTo("studentId", uid).get().get();
            for (DocumentSnapshot tDoc : testSnap.getDocuments()) {
                totalEvaluationsCount++;
                String tSt = tDoc.getString("status");
                if ("completed".equalsIgnoreCase(tSt) || "PASSED".equalsIgnoreCase(tSt) || "SUBMITTED".equalsIgnoreCase(tSt)) {
                    completedTests++;
                    Double score = tDoc.getDouble("score");
                    if (score != null) {
                        totalTestScore += score;
                    }
                } else if ("absent".equalsIgnoreCase(tSt) || "MISSED".equalsIgnoreCase(tSt)) {
                    absentTests++;
                } else if ("excused_meeting".equalsIgnoreCase(tSt) || "excused".equalsIgnoreCase(tSt)) {
                    totalExcuseCount++;
                }
            }
        } catch (Exception e) {
            log.warn("Error querying proctored test records for candidate [{}]: {}", uid, e.getMessage());
        }

        // Division-by-Zero Guard for Average Test Score
        if (completedTests > 0) {
            double avgScore = totalTestScore / (double) completedTests;
            summary.put("averageTestScore", Math.round(avgScore * 10.0) / 10.0);
        } else {
            summary.put("averageTestScore", null); // UI will render explicit microcopy "No exams recorded"
        }
        summary.put("completedExamCount", completedTests);
        summary.put("absentExamCount", absentTests);

        // 5. Calculate Excuse Usage Percentage with Division Guard
        double excusePercent = (totalEvaluationsCount > 0)
                ? Math.min(100.0, ((double) totalExcuseCount / (double) totalEvaluationsCount) * 100.0)
                : 0.0;
        summary.put("excuseUsagePercentage", Math.round(excusePercent * 10.0) / 10.0);

        // 6. Evaluate Institutional Risk Level across Warnings Ledger
        int highlightedMonths = 0;
        try {
            QuerySnapshot warnSnap = db.collection("warnings").whereEqualTo("highlighted", true).get().get();
            for (DocumentSnapshot wDoc : warnSnap.getDocuments()) {
                String docId = wDoc.getId();
                if (docId.startsWith(uid + "_")) {
                    highlightedMonths++;
                }
            }
        } catch (Exception e) {
            log.warn("Error querying warning ledger for candidate [{}]: {}", uid, e.getMessage());
        }

        String riskLevel = "Low";
        if (highlightedMonths == 1) {
            riskLevel = "Medium";
        } else if (highlightedMonths >= 2) {
            riskLevel = "High";
        }
        summary.put("riskLevel", riskLevel);
        summary.put("highlightedMonthsCount", highlightedMonths);

        return summary;
    }

    /**
     * Retrieves permanent final completion summary report for a completed candidate.
     */
    public Map<String, Object> getCompletionSummary(String uid) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                DocumentSnapshot doc = db.collection("completion_summaries").document(uid).get().get();
                if (doc.exists() && doc.getData() != null) {
                    return doc.getData();
                }
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore offline or unreachable during completion summary lookup for candidate [{}]: {}", uid, e.getMessage());
        }

        // Return dev simulation fallbacks when operating in offline demo mode
        if (simulatedCompletionRegistry.containsKey(uid)) {
            return simulatedCompletionRegistry.get(uid);
        }
        // If requesting a test intern ID during dev mode, return fallback completed profile
        if ("dev-stud-106".equalsIgnoreCase(uid) || "dev-student-id".equalsIgnoreCase(uid) || "CS001".equalsIgnoreCase(uid)) {
            return simulatedCompletionRegistry.get("CS001");
        }

        return null;
    }

    /**
     * Retrieves list of all completed candidate summaries for HOD administrative certification.
     */
    public List<Map<String, Object>> getAllCompletedStudents() {
        List<Map<String, Object>> completedList = new ArrayList<>();
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                QuerySnapshot snap = db.collection("completion_summaries").get().get();
                for (DocumentSnapshot doc : snap.getDocuments()) {
                    if (doc.getData() != null) {
                        completedList.add(doc.getData());
                    }
                }
                if (!completedList.isEmpty()) {
                    return completedList;
                }
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore offline during HOD completed ledger lookup: {}", e.getMessage());
        }

        // Fallback to simulated registry for institutional demonstrations
        for (Map<String, Object> rec : simulatedCompletionRegistry.values()) {
            completedList.add(rec);
        }
        return completedList;
    }

    private void initializeMockCompletionRecords() {
        long now = System.currentTimeMillis();
        Map<String, Object> mock1 = new HashMap<>();
        mock1.put("uid", "CS001");
        mock1.put("studentName", "Aditya Sharma");
        mock1.put("branch", "Computer Science & Engineering");
        mock1.put("mentorName", "Dr. Rajesh K.");
        mock1.put("internshipDomain", "Cloud Infrastructure & DevOps");
        mock1.put("joiningDate", "2026-04-01");
        mock1.put("completionDate", LocalDate.now(applicationZoneId).minusDays(1).toString());
        mock1.put("completedAt", now - 86400000L);
        mock1.put("isFinalized", true);
        mock1.put("expectedWorkingDays", 62);
        mock1.put("attendancePercentage", 93.5);
        mock1.put("verifiedAttendanceDays", 58);

        mock1.put("acceptedDiariesCount", 57);
        mock1.put("averageTestScore", 84.5);
        mock1.put("completedExamCount", 14);
        mock1.put("absentExamCount", 1);
        mock1.put("excuseUsagePercentage", 6.5);
        mock1.put("riskLevel", "Low");
        mock1.put("highlightedMonthsCount", 0);
        simulatedCompletionRegistry.put("CS001", mock1);

        Map<String, Object> mock2 = new HashMap<>();
        mock2.put("uid", "CS002");
        mock2.put("studentName", "Priya Patel");
        mock2.put("branch", "Information Technology");
        mock2.put("mentorName", "Dr. Meenakshi S.");
        mock2.put("internshipDomain", "Artificial Intelligence & Data Science");
        mock2.put("joiningDate", "2026-03-15");
        mock2.put("completionDate", LocalDate.now(applicationZoneId).minusDays(5).toString());
        mock2.put("completedAt", now - 432000000L);
        mock2.put("isFinalized", true);
        mock2.put("expectedWorkingDays", 70);
        mock2.put("attendancePercentage", 78.5);
        mock2.put("verifiedAttendanceDays", 55);

        mock2.put("acceptedDiariesCount", 52);
        mock2.put("averageTestScore", 71.0);
        mock2.put("completedExamCount", 12);
        mock2.put("absentExamCount", 4);
        mock2.put("excuseUsagePercentage", 28.0);
        mock2.put("riskLevel", "High");
        mock2.put("highlightedMonthsCount", 2);
        simulatedCompletionRegistry.put("CS002", mock2);
    }
}
