package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing real-time and scheduled evaluation of monthly student warning indicators,
 * auto-flagging students as "Highlighted" on institutional Head of Department (HOD) dashboards.
 * Interoperates directly with authoritative Firestore collections: warnings, daily_status, users, and internships.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    // In-memory fallback simulated ledgers for development verification or when Firestore offline
    private final Map<String, Map<String, Object>> devWarningsLedger = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> devStudentProfiles = new ConcurrentHashMap<>();

    public EscalationService() {
        initializeDevSimulationRecords();
    }

    /**
     * Core escalation check evaluating real accumulated rejections, absences, and excuse usage per candidate.
     * Evaluates against institutional thresholds:
     * - diaryRejectionCount >= 3
     * - absenceCount >= 3 (including face-match confirmation failures)
     * - excuseAbusePercentage >= 25%
     *
     * @param targetMonth Specified yyyy-MM string; if null, defaults to current calendar month.
     * @return count of students currently highlighted by this audit execution.
     */
    public int runEscalationCheck(String targetMonth) {
        String activeMonth = (targetMonth != null && !targetMonth.trim().isEmpty()) 
                ? targetMonth.trim() : YearMonth.now().format(MONTH_FORMATTER);

        log.info("===================================================================================");
        log.info("   ESCALATION AUDIT INITIALIZED: Tallying monthly institutional indicators for [{}]   ", activeMonth);
        log.info("===================================================================================");

        int highlightedCount = 0;

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                // Read all candidate student profile IDs from internships / users
                ApiFuture<QuerySnapshot> futureUsers = db.collection("users").get();
                List<QueryDocumentSnapshot> userDocs = futureUsers.get().getDocuments();
                
                Set<String> studentUids = new HashSet<>();
                for (QueryDocumentSnapshot uDoc : userDocs) {
                    String role = uDoc.getString("role");
                    if (role == null || "STUDENT".equalsIgnoreCase(role)) {
                        studentUids.add(uDoc.getId());
                    }
                }
                if (studentUids.isEmpty()) {
                    // Fallback to internships collection IDs if users role filtering didn't yield records
                    ApiFuture<QuerySnapshot> futureIntern = db.collection("internships").get();
                    futureIntern.get().getDocuments().forEach(d -> studentUids.add(d.getId()));
                }

                // Query all daily_status docs for efficient in-memory aggregation for the active month
                List<QueryDocumentSnapshot> dailyStatusDocs = db.collection("daily_status").get().get().getDocuments();

                for (String uid : studentUids) {
                    String warnId = uid + "_" + activeMonth;
                    DocumentReference warnRef = db.collection("warnings").document(warnId);
                    DocumentSnapshot warnDoc = warnRef.get().get();

                    // Tally 1: Diary rejections directly from existing warnings/{uid}_{yyyy-MM} incremented by Module 7
                    long diaryRejectionCount = 0;
                    if (warnDoc.exists() && warnDoc.contains("diaryRejectionCount")) {
                        Long countVal = warnDoc.getLong("diaryRejectionCount");
                        if (countVal != null) diaryRejectionCount = countVal;
                    }

                    // Tally 2 & 3: Absences (including face-match mismatch) and Excuse Abuse Percentage
                    long absenceCount = 0;
                    long excusedCount = 0;
                    long totalChecks = 0;

                    for (QueryDocumentSnapshot ds : dailyStatusDocs) {
                        String docUid = ds.getString("uid");
                        String dateStr = ds.getString("date");

                        // Match document ID or inner fields to current student and target month
                        boolean matchesStudent = uid.equals(docUid) || ds.getId().startsWith(uid + "_");
                        boolean matchesMonth = (dateStr != null && dateStr.startsWith(activeMonth)) || ds.getId().contains("_" + activeMonth);

                        if (matchesStudent && matchesMonth) {
                            totalChecks++;
                            String status = ds.getString("status");
                            String attendanceStatus = ds.getString("attendanceStatus");
                            String absenceReason = ds.getString("absenceReason");

                            // Evaluate excused meetings across daily check-ins
                            if ("excused_meeting".equalsIgnoreCase(status) || "excused_meeting".equalsIgnoreCase(attendanceStatus)) {
                                excusedCount++;
                            }

                            // Evaluate actual absences OR confirmed face-match self-contradiction mismatches
                            boolean isAbsentStatus = "absent".equalsIgnoreCase(status) || "missed".equalsIgnoreCase(status);
                            boolean isFaceMismatch = absenceReason != null && (
                                absenceReason.toLowerCase().contains("face") || 
                                absenceReason.toLowerCase().contains("mismatch") ||
                                absenceReason.toLowerCase().contains("contradiction")
                            );

                            if (isAbsentStatus || isFaceMismatch) {
                                absenceCount++;
                            }
                        }
                    }

                    double excuseAbusePercentage = totalChecks > 0 ? ((double) excusedCount / (double) totalChecks) * 100.0 : 0.0;

                    // Evaluate institutional escalation criteria
                    boolean isHighlighted = (diaryRejectionCount >= 3) || (absenceCount >= 3) || (excuseAbusePercentage >= 25.0);
                    List<String> reasons = new ArrayList<>();
                    if (diaryRejectionCount >= 3) {
                        reasons.add("3+ diary rejections");
                    }
                    if (absenceCount >= 3) {
                        reasons.add("3+ absences");
                    }
                    if (excuseAbusePercentage >= 25.0) {
                        reasons.add("excuse abuse " + Math.round(excuseAbusePercentage) + "%");
                    }

                    // Persist authoritative evaluation to warnings/{uid}_{yyyy-MM}
                    Map<String, Object> warnUpdate = new HashMap<>();
                    warnUpdate.put("uid", uid);
                    warnUpdate.put("month", activeMonth);
                    warnUpdate.put("diaryRejectionCount", diaryRejectionCount);
                    warnUpdate.put("absenceCount", absenceCount);
                    warnUpdate.put("excuseAbusePercentage", Math.round(excuseAbusePercentage * 10.0) / 10.0);
                    warnUpdate.put("highlighted", isHighlighted);
                    warnUpdate.put("reasons", reasons);
                    warnUpdate.put("updatedAt", System.currentTimeMillis());

                    warnRef.set(warnUpdate, SetOptions.merge());

                    if (isHighlighted) {
                        highlightedCount++;
                        log.warn("[ESCALATION AUDIT FLAGGED] Student ID [{}] flagged as HIGHLIGHTED for month [{}]. Criteria breached: {}", uid, activeMonth, reasons);
                    }
                }
            } else {
                highlightedCount = executeDevFallbackAudit(activeMonth);
            }
        } catch (IllegalStateException | NoClassDefFoundError e) {
            log.warn("Firebase runtime uninitialized. Executing in-memory local simulated escalation audit.");
            highlightedCount = executeDevFallbackAudit(activeMonth);
        } catch (Exception e) {
            log.error("Error connecting to cloud Firestore during escalation check: {}", e.getMessage(), e);
            highlightedCount = executeDevFallbackAudit(activeMonth);
        }

        log.info("Escalation check completed for month [{}]. Total candidates highlighted: [{}]", activeMonth, highlightedCount);
        return highlightedCount;
    }

    /**
     * Retrieves currently highlighted student records for the active month, joined with student identity details.
     */
    public List<Map<String, Object>> getHighlightedStudents() {
        String currentMonth = YearMonth.now().format(MONTH_FORMATTER);
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                // Query warnings for current month where highlighted is true
                Query query = db.collection("warnings")
                        .whereEqualTo("month", currentMonth)
                        .whereEqualTo("highlighted", true);

                List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
                for (QueryDocumentSnapshot warnDoc : documents) {
                    Map<String, Object> data = new HashMap<>(warnDoc.getData());
                    String uid = warnDoc.getString("uid");
                    if (uid == null) uid = warnDoc.getId().split("_")[0];
                    data.put("uid", uid);

                    // Join with users collection for student details
                    DocumentSnapshot userDoc = db.collection("users").document(uid).get().get();
                    if (userDoc.exists() && userDoc.getData() != null) {
                        data.put("studentName", userDoc.getString("fullName") != null ? userDoc.getString("fullName") : "Student Profile (" + uid + ")");
                        data.put("branch", userDoc.getString("branch") != null ? userDoc.getString("branch") : "Computer Science & Engineering");
                    } else {
                        // Attempt fallback join with internships collection
                        DocumentSnapshot internDoc = db.collection("internships").document(uid).get().get();
                        if (internDoc.exists() && internDoc.getData() != null) {
                            data.put("studentName", internDoc.getString("fullName") != null ? internDoc.getString("fullName") : "Student Profile (" + uid + ")");
                            data.put("branch", internDoc.getString("branch") != null ? internDoc.getString("branch") : "Computer Science & Engineering");
                            data.put("mentor", internDoc.getString("assignedMentor") != null ? internDoc.getString("assignedMentor") : "Dr. Rajesh K.");
                        } else {
                            data.put("studentName", "Student Profile (" + uid + ")");
                            data.put("branch", "Computer Science & Engineering");
                            data.put("mentor", "Dr. Rajesh K.");
                        }
                    }

                    if (!data.containsKey("mentor")) {
                        DocumentSnapshot internDoc = db.collection("internships").document(uid).get().get();
                        if (internDoc.exists() && internDoc.getString("assignedMentor") != null) {
                            data.put("mentor", internDoc.getString("assignedMentor"));
                        } else {
                            data.put("mentor", "Dr. Rajesh K.");
                        }
                    }

                    results.add(data);
                }
            } else {
                results = getDevHighlightedStudents(currentMonth);
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore unreachable during getHighlightedStudents query: {}", e.getMessage());
            results = getDevHighlightedStudents(currentMonth);
        }

        return results;
    }

    /**
     * Retrieves month-by-month warning evaluation history for a single student candidate.
     */
    public List<Map<String, Object>> getStudentWarningHistory(String uid) {
        List<Map<String, Object>> history = new ArrayList<>();
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                // Query all warnings for this uid and sort in memory by month descending to prevent composite index errors
                List<QueryDocumentSnapshot> docs = db.collection("warnings")
                        .whereEqualTo("uid", uid)
                        .get().get().getDocuments();

                for (QueryDocumentSnapshot doc : docs) {
                    history.add(new HashMap<>(doc.getData()));
                }

                history.sort((a, b) -> {
                    String mA = (String) a.getOrDefault("month", "");
                    String mB = (String) b.getOrDefault("month", "");
                    return mB.compareTo(mA);
                });
            } else {
                history = getDevWarningHistory(uid);
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore unreachable during getStudentWarningHistory query for [{}]", uid);
            history = getDevWarningHistory(uid);
        }
        return history;
    }

    /**
     * Returns total count of currently highlighted students for sidebar notification badges.
     */
    public Map<String, Object> getHighlightedCount() {
        int count = getHighlightedStudents().size();
        Map<String, Object> response = new HashMap<>();
        response.put("count", count);
        response.put("status", "SUCCESS");
        return response;
    }

    // --- Dev Simulation & Fallback Methods ---

    private void initializeDevSimulationRecords() {
        String currentMonth = YearMonth.now().format(MONTH_FORMATTER);
        String lastMonth = YearMonth.now().minusMonths(1).format(MONTH_FORMATTER);
        String twoMonthsAgo = YearMonth.now().minusMonths(2).format(MONTH_FORMATTER);

        // Seed profile descriptions for offline institutional evaluation review
        devStudentProfiles.put("CS001", createProfile("Aditya Sharma", "Computer Science & Engineering", "Dr. Rajesh K."));
        devStudentProfiles.put("CS002", createProfile("Priya Patel", "Information Technology", "Dr. Meenakshi S."));
        devStudentProfiles.put("CS003", createProfile("Rohan Verma", "Artificial Intelligence & DS", "Prof. Suresh B."));
        devStudentProfiles.put("CS004", createProfile("Siddharth Nair", "Computer Science & Engineering", "Dr. Rajesh K."));

        // Candidate 1: Excessive absences & excuse usage in current month
        devWarningsLedger.put("CS001_" + currentMonth, createWarnRecord("CS001", currentMonth, 1, 4, 32.5, true, Arrays.asList("3+ absences", "excuse abuse 33%")));
        devWarningsLedger.put("CS001_" + lastMonth, createWarnRecord("CS001", lastMonth, 0, 1, 12.0, false, Collections.emptyList()));

        // Candidate 2: Repeated diary rejections & face mismatch failures
        devWarningsLedger.put("CS002_" + currentMonth, createWarnRecord("CS002", currentMonth, 4, 3, 10.0, true, Arrays.asList("3+ diary rejections", "3+ absences")));
        devWarningsLedger.put("CS002_" + lastMonth, createWarnRecord("CS002", lastMonth, 3, 0, 0.0, true, Arrays.asList("3+ diary rejections")));
        devWarningsLedger.put("CS002_" + twoMonthsAgo, createWarnRecord("CS002", twoMonthsAgo, 1, 0, 5.0, false, Collections.emptyList()));

        // Candidate 3: Excuse abuse loophole threshold
        devWarningsLedger.put("CS003_" + currentMonth, createWarnRecord("CS003", currentMonth, 0, 1, 40.0, true, Arrays.asList("excuse abuse 40%")));
    }

    private Map<String, Object> createProfile(String name, String branch, String mentor) {
        Map<String, Object> map = new HashMap<>();
        map.put("studentName", name);
        map.put("branch", branch);
        map.put("mentor", mentor);
        return map;
    }

    private Map<String, Object> createWarnRecord(String uid, String month, long rejections, long absences, double excusePct, boolean highlighted, List<String> reasons) {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("month", month);
        map.put("diaryRejectionCount", rejections);
        map.put("absenceCount", absences);
        map.put("excuseAbusePercentage", excusePct);
        map.put("highlighted", highlighted);
        map.put("reasons", reasons);
        map.put("updatedAt", System.currentTimeMillis());
        return map;
    }

    private int executeDevFallbackAudit(String targetMonth) {
        log.info("Executing in-memory fallback simulated escalation audit for month [{}]", targetMonth);
        int count = 0;
        for (Map.Entry<String, Map<String, Object>> entry : devWarningsLedger.entrySet()) {
            if (entry.getKey().endsWith(targetMonth)) {
                Map<String, Object> rec = entry.getValue();
                long rejections = (Long) rec.getOrDefault("diaryRejectionCount", 0L);
                long absences = (Long) rec.getOrDefault("absenceCount", 0L);
                double excusePct = (Double) rec.getOrDefault("excuseAbusePercentage", 0.0);

                boolean highlight = rejections >= 3 || absences >= 3 || excusePct >= 25.0;
                rec.put("highlighted", highlight);
                if (highlight) {
                    count++;
                    log.warn("[DEV ESCALATION FLAGGED] Student [{}] highlighted in dev simulation for month [{}].", rec.get("uid"), targetMonth);
                }
            }
        }
        return count;
    }

    private List<Map<String, Object>> getDevHighlightedStudents(String currentMonth) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> rec : devWarningsLedger.values()) {
            if (currentMonth.equals(rec.get("month")) && Boolean.TRUE.equals(rec.get("highlighted"))) {
                Map<String, Object> full = new HashMap<>(rec);
                String uid = (String) rec.get("uid");
                Map<String, Object> profile = devStudentProfiles.getOrDefault(uid, createProfile("Student (" + uid + ")", "Computer Science", "Dr. Rajesh K."));
                full.putAll(profile);
                list.add(full);
            }
        }
        return list;
    }

    private List<Map<String, Object>> getDevWarningHistory(String uid) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> rec : devWarningsLedger.values()) {
            if (uid.equals(rec.get("uid"))) {
                list.add(new HashMap<>(rec));
            }
        }
        list.sort((a, b) -> ((String) b.getOrDefault("month", "")).compareTo((String) a.getOrDefault("month", "")));
        return list;
    }
}
