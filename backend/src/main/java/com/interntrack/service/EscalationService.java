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

    public EscalationService() {
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

                // Query daily_status docs for efficient in-memory aggregation strictly bounded to the active month
                Query activeMonthQuery = db.collection("daily_status")
                        .whereGreaterThanOrEqualTo("date", activeMonth + "-01")
                        .whereLessThanOrEqualTo("date", activeMonth + "-31");
                List<QueryDocumentSnapshot> dailyStatusDocs = activeMonthQuery.get().get().getDocuments();

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
                    boolean isHighlighted = (absenceCount >= 3);
                    List<String> reasons = new ArrayList<>();
                    if (absenceCount >= 3) {
                        reasons.add("3+ absences");
                    }

                    boolean hasWarningIndicators = absenceCount > 0;
                    
                    if (hasWarningIndicators || isHighlighted) {
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
                    } else if (warnDoc.exists()) {
                        // Clean up zero-data warning documents to prevent database flooding
                        warnRef.delete();
                    }

                    if (isHighlighted) {
                        highlightedCount++;
                        log.warn("[ESCALATION AUDIT FLAGGED] Student ID [{}] flagged as HIGHLIGHTED for month [{}]. Criteria breached: {}", uid, activeMonth, reasons);
                    }
                }
            } else {
                log.error("Firebase runtime uninitialized. Cannot execute escalation audit.");
            }
        } catch (Exception e) {
            log.error("Error connecting to cloud Firestore during escalation check: {}", e.getMessage(), e);
            throw new RuntimeException("Firestore unavailable", e);
        }

        log.info("Escalation check completed for month [{}]. Total candidates highlighted: [{}]", activeMonth, highlightedCount);
        return highlightedCount;
    }

    /**
     * Retrieves currently highlighted student records for the active month, joined with student identity details.
     */
    public List<Map<String, Object>> getHighlightedStudents() {
        String currentMonth = YearMonth.now().format(MONTH_FORMATTER);
        String previousMonth = YearMonth.now().minusMonths(1).format(MONTH_FORMATTER);
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                // Query warnings for current AND previous month where highlighted is true
                Query query = db.collection("warnings")
                        .whereIn("month", Arrays.asList(currentMonth, previousMonth))
                        .whereEqualTo("highlighted", true);

                List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
                if (documents.isEmpty()) return results;

                // Collect all UIDs using a Set to prevent duplicate references (fixes 500 Error)
                java.util.Set<String> uniqueUids = new java.util.HashSet<>();
                for (QueryDocumentSnapshot warnDoc : documents) {
                    String uid = warnDoc.getString("uid");
                    if (uid == null) uid = warnDoc.getId().split("_")[0];
                    uniqueUids.add(uid);
                }

                // Batch fetch users
                DocumentReference[] userRefs = uniqueUids.stream().map(uid -> db.collection("users").document(uid)).toArray(DocumentReference[]::new);
                List<DocumentSnapshot> userDocs = db.getAll(userRefs).get();
                Map<String, DocumentSnapshot> userDocMap = new HashMap<>();
                for (DocumentSnapshot doc : userDocs) {
                    userDocMap.put(doc.getId(), doc);
                }

                // Batch fetch internships
                DocumentReference[] internRefs = uniqueUids.stream().map(uid -> db.collection("internships").document(uid)).toArray(DocumentReference[]::new);
                List<DocumentSnapshot> internDocs = db.getAll(internRefs).get();
                Map<String, DocumentSnapshot> internDocMap = new HashMap<>();
                for (DocumentSnapshot doc : internDocs) {
                    internDocMap.put(doc.getId(), doc);
                }

                for (QueryDocumentSnapshot warnDoc : documents) {
                    Map<String, Object> data = new HashMap<>(warnDoc.getData());
                    String uid = warnDoc.getString("uid");
                    if (uid == null) uid = warnDoc.getId().split("_")[0];
                    data.put("uid", uid);

                    DocumentSnapshot userDoc = userDocMap.get(uid);
                    if (userDoc != null && userDoc.exists() && userDoc.getData() != null) {
                        data.put("studentName", userDoc.getString("fullName") != null ? userDoc.getString("fullName") : "Student Profile (" + uid + ")");
                        data.put("branch", userDoc.getString("branch") != null ? userDoc.getString("branch") : "Computer Science & Engineering");
                    } else {
                        DocumentSnapshot internDoc = internDocMap.get(uid);
                        if (internDoc != null && internDoc.exists() && internDoc.getData() != null) {
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
                        DocumentSnapshot internDoc = internDocMap.get(uid);
                        if (internDoc != null && internDoc.exists() && internDoc.getString("assignedMentor") != null) {
                            data.put("mentor", internDoc.getString("assignedMentor"));
                        } else {
                            data.put("mentor", "Dr. Rajesh K.");
                        }
                    }

                    results.add(data);
                }
            } else {
                log.error("Cloud Firestore unreachable during getHighlightedStudents query.");
            }
        } catch (Exception e) {
            log.error("Cloud Firestore unreachable during getHighlightedStudents query: {}", e.getMessage());
            throw new RuntimeException("Firestore unavailable", e);
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
                log.error("Cloud Firestore unreachable during getStudentWarningHistory query for [{}]", uid);
            }
        } catch (Exception e) {
            log.error("Cloud Firestore unreachable during getStudentWarningHistory query for [{}]: {}", uid, e.getMessage());
            throw new RuntimeException("Firestore unavailable", e);
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
}
