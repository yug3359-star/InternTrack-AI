package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.exception.InvalidRegistrationException;
import com.interntrack.dto.SecurityValidationDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service handling departmental HOD evaluation of student internship registrations.
 * Executes paginated two-read Firestore queries and manages application status transitions and filtering.
 */
@Service
public class HodService {

    private static final Logger log = LoggerFactory.getLogger(HodService.class);
    private static final int PAGE_SIZE = 20;

    // In-memory simulation registry to enable team review evaluations without active cloud database connectivity
    private final Map<String, Map<String, Object>> devSimulatedApplications = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.interntrack.service.StatusService statusService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.interntrack.scheduler.AttendancePopupJob attendancePopupJob;

    @org.springframework.beans.factory.annotation.Autowired
    private com.interntrack.scheduler.EngagementPopupJob engagementPopupJob;

    public HodService() {
    }

    /**
     * Backward compatibility wrapper defaulting to Applied status query.
     */
    public Map<String, Object> getPendingApplications(int page) {
        return getPendingApplications(page, "ALL");
    }

    /**
     * Retrieves a paginated list of applications filtered by status, ordered by appropriate chronology.
     * Uses a two-read approach per item to combine internship record with user profile details.
     */
    public Map<String, Object> getPendingApplications(int page, String statusFilter) {
        int targetPage = Math.max(1, page);
        List<Map<String, Object>> matchedRecords = new ArrayList<>();
        String cleanFilter = (statusFilter == null || statusFilter.trim().isEmpty() || "All Statuses".equalsIgnoreCase(statusFilter)) 
                ? "ALL" : statusFilter.trim().toUpperCase();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Query query = db.collection("internships");
                if (!"ALL".equalsIgnoreCase(cleanFilter)) {
                    // Match specific status sentence case (e.g., "Applied", "Approved", "Ongoing")
                    String targetStatus = formatStatusString(cleanFilter);
                    query = query.whereEqualTo("status", targetStatus);
                }
                
                ApiFuture<QuerySnapshot> future = query.get();
                List<? extends DocumentSnapshot> documents = future.get().getDocuments();

                // Batch fetch all user profiles in a single query to prevent N+1 30-second timeouts
                java.util.Set<String> uids = new java.util.HashSet<>();
                for (DocumentSnapshot doc : documents) {
                    uids.add(doc.getId());
                }
                Map<String, DocumentSnapshot> userDocMap = new HashMap<>();
                if (!uids.isEmpty()) {
                    com.google.cloud.firestore.DocumentReference[] userRefs = uids.stream()
                            .map(uid -> db.collection("users").document(uid))
                            .toArray(com.google.cloud.firestore.DocumentReference[]::new);
                    List<DocumentSnapshot> userDocs = db.getAll(userRefs).get();
                    for (DocumentSnapshot uDoc : userDocs) {
                        userDocMap.put(uDoc.getId(), uDoc);
                    }
                }

                for (DocumentSnapshot doc : documents) {
                    Map<String, Object> internData = doc.getData();
                    if (internData != null) {
                        Map<String, Object> record = new HashMap<>(internData);
                        record.put("uid", doc.getId());

                        // Read 2: Retrieve from pre-fetched batch map
                        DocumentSnapshot userDoc = userDocMap.get(doc.getId());
                        if (userDoc != null && userDoc.exists() && userDoc.getData() != null) {
                            record.put("fullName", userDoc.getString("fullName"));
                            record.put("collegeEmail", userDoc.getString("collegeEmail"));
                            record.put("branch", userDoc.getString("branch"));
                            record.put("rollNo", userDoc.getString("rollNo"));
                            record.put("enrollmentNo", userDoc.getString("enrollmentNo"));
                            record.put("section", userDoc.getString("section"));
                        } else {
                            record.put("fullName", internData.getOrDefault("fullName", "Unverified Profile (" + doc.getId() + ")"));
                            record.put("collegeEmail", internData.getOrDefault("collegeEmail", "unknown@college.edu"));
                            record.put("branch", internData.getOrDefault("branch", "Computer Science & Engineering"));
                        }
                        matchedRecords.add(record);
                    }
                }
            } else {
                matchedRecords = getDevRecordsFiltered(cleanFilter);
            }
        } catch (IllegalStateException | NoClassDefFoundError e) {
            log.warn("Firebase runtime uninitialized. Utilizing in-memory simulated evaluation records.");
            matchedRecords = getDevRecordsFiltered(cleanFilter);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("default FirebaseApp is not initialized") || e.getMessage().contains("has not been initialized"))) {
                log.warn("Firestore cloud offline. Falling back to local simulation ledger for review demonstration.");
                matchedRecords = getDevRecordsFiltered(cleanFilter);
            } else {
                log.error("Failed to query applications from Firestore: {}", e.getMessage(), e);
                matchedRecords = getDevRecordsFiltered(cleanFilter);
            }
        }

        // Execute chronological sort
        matchedRecords.sort((a, b) -> Long.compare(
                ((Number) b.getOrDefault("createdAt", 0L)).longValue(),
                ((Number) a.getOrDefault("createdAt", 0L)).longValue()
        ));

        // Execute deterministic manual slice pagination (20 per page)
        int totalRecords = matchedRecords.size();
        int totalPages = (int) Math.ceil((double) totalRecords / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;

        int fromIndex = (targetPage - 1) * PAGE_SIZE;
        List<Map<String, Object>> paginatedSlice;
        if (fromIndex >= totalRecords) {
            paginatedSlice = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + PAGE_SIZE, totalRecords);
            paginatedSlice = matchedRecords.subList(fromIndex, toIndex);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("applications", paginatedSlice);
        response.put("currentPage", targetPage);
        response.put("pageSize", PAGE_SIZE);
        response.put("totalRecords", totalRecords);
        response.put("totalPages", totalPages);
        response.put("activeFilter", cleanFilter);
        return response;
    }

    /**
     * Retrieves a list of students assigned to a specific college mentor.
     */
    public List<Map<String, Object>> getMentorStudents(String mentorName) {
        List<Map<String, Object>> matchedRecords = new ArrayList<>();
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Query query = db.collection("internships").whereEqualTo("collegeMentor", mentorName);
                
                ApiFuture<QuerySnapshot> future = query.get();
                List<? extends DocumentSnapshot> documents = future.get().getDocuments();
                for (DocumentSnapshot doc : documents) {
                    Map<String, Object> internData = doc.getData();
                    if (internData != null) {
                        Map<String, Object> record = new HashMap<>(internData);
                        record.put("uid", doc.getId());

                        // Read 2: Second read for matching users/{uid} doc to retrieve fullName and collegeEmail
                        DocumentSnapshot userDoc = db.collection("users").document(doc.getId()).get().get();
                        if (userDoc.exists() && userDoc.getData() != null) {
                            record.put("fullName", userDoc.getString("fullName"));
                            record.put("collegeEmail", userDoc.getString("collegeEmail"));
                            record.put("branch", userDoc.getString("branch"));
                            record.put("rollNo", userDoc.getString("rollNo"));
                            record.put("enrollmentNo", userDoc.getString("enrollmentNo"));
                            record.put("section", userDoc.getString("section"));
                        } else {
                            record.put("fullName", internData.getOrDefault("fullName", "Unverified Profile (" + doc.getId() + ")"));
                            record.put("collegeEmail", internData.getOrDefault("collegeEmail", "unknown@college.edu"));
                            record.put("branch", internData.getOrDefault("branch", "Computer Science & Engineering"));
                        }

                        Object attStr = internData.get("attendancePercentage");
                        if (attStr != null) {
                            try {
                                record.put("attendancePercentage", Double.parseDouble(attStr.toString()));
                            } catch (NumberFormatException e) {
                                record.put("attendancePercentage", 0.0);
                            }
                        } else {
                            record.put("attendancePercentage", 0.0);
                        }

                        matchedRecords.add(record);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query mentor students from Firestore: {}", e.getMessage());
        }

        return matchedRecords;
    }

    /**
     * Retrieves complete detail for a single application UID.
     */
    public Map<String, Object> getApplicationDetail(String uid) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                DocumentSnapshot internDoc = db.collection("internships").document(uid).get().get();
                if (!internDoc.exists()) {
                    throw new InvalidRegistrationException("Target application record does not exist in central database.");
                }
                Map<String, Object> detail = new HashMap<>(internDoc.getData());
                detail.put("uid", uid);

                DocumentSnapshot userDoc = db.collection("users").document(uid).get().get();
                if (userDoc.exists() && userDoc.getData() != null) {
                    detail.put("fullName", userDoc.getString("fullName"));
                    detail.put("collegeEmail", userDoc.getString("collegeEmail"));
                    detail.put("branch", userDoc.getString("branch"));
                } else {
                    detail.putIfAbsent("fullName", "Unverified Profile (" + uid + ")");
                    detail.putIfAbsent("collegeEmail", "unknown@college.edu");
                    detail.putIfAbsent("branch", "Computer Science & Engineering");
                }
                return detail;
            }
        } catch (Exception e) {
            log.warn("Falling back to local simulation detail for uid {}: {}", uid, e.getMessage());
        }

        if (devSimulatedApplications.containsKey(uid)) {
            return devSimulatedApplications.get(uid);
        }
        throw new InvalidRegistrationException("Application record not found for student ID: " + uid);
    }

    /**
     * Approves a student internship application, setting status to "Approved".
     */
    public Map<String, Object> approveApplication(String uid, String hodUid, String collegeMentor) {
        long timestamp = System.currentTimeMillis();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "Approved");
                updates.put("approvedAt", timestamp);
                updates.put("approvedBy", hodUid);
                updates.put("collegeMentor", collegeMentor);
                db.collection("internships").document(uid).update(updates).get();
                log.info("HOD [{}] approved internship application for student [{}] at timestamp [{}] with mentor [{}]", hodUid, uid, timestamp, collegeMentor);
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore update unreachable during approval commit for {}: {}", uid, e.getMessage());
        }

        if (devSimulatedApplications.containsKey(uid)) {
            Map<String, Object> record = devSimulatedApplications.get(uid);
            record.put("status", "Approved");
            record.put("approvedAt", timestamp);
            record.put("approvedBy", hodUid);
            record.put("collegeMentor", collegeMentor);
            log.info("Dev Simulation: HOD [{}] approved mock application [{}] with mentor [{}]", hodUid, uid, collegeMentor);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("status", "Approved");
        response.put("message", "Application approved");
        
        // INSTANT AUTOMATION: Instantly evaluate Ongoing transition and rebuild attendance schedule
        if (statusService != null && attendancePopupJob != null && engagementPopupJob != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    log.info("Executing immediate status transition and attendance generation automation post-approval...");
                    statusService.runStatusTransitionCheck();
                    attendancePopupJob.generateDailyAttendanceChecks();
                    engagementPopupJob.generateDailyEngagementSchedules();
                } catch (Exception ex) {
                    log.error("Failed to execute instant automation", ex);
                }
            });
        }
        
        return response;
    }

    /**
     * Rejects a student internship application, recording an optional rejection reason.
     */
    public Map<String, Object> rejectApplication(String uid, String reason, String hodUid) {
        long timestamp = System.currentTimeMillis();
        String cleanReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : null;

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "Rejected");
                updates.put("rejectedAt", timestamp);
                updates.put("rejectedBy", hodUid);
                updates.put("rejectionReason", cleanReason);
                db.collection("internships").document(uid).update(updates).get();
                log.info("HOD [{}] rejected internship application for student [{}] with reason: {}", hodUid, uid, cleanReason);
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore update unreachable during rejection commit for {}: {}", uid, e.getMessage());
        }

        if (devSimulatedApplications.containsKey(uid)) {
            Map<String, Object> record = devSimulatedApplications.get(uid);
            record.put("status", "Rejected");
            record.put("rejectedAt", timestamp);
            record.put("rejectedBy", hodUid);
            record.put("rejectionReason", cleanReason);
            log.info("Dev Simulation: HOD [{}] rejected mock application [{}] with reason: {}", hodUid, uid, cleanReason);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("status", "Rejected");
        response.put("message", "Application rejected");
        return response;
    }

    public Map<String, Object> createApplication(SecurityValidationDtos.ApplicationCrudDto dto, String hodUid) {
        String newUid = "man-app-" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis();

        Map<String, Object> record = new HashMap<>();
        record.put("uid", newUid);
        record.put("fullName", dto.getFullName());
        record.put("collegeEmail", dto.getCollegeEmail());
        record.put("branch", dto.getBranch());
        record.put("internshipDomain", dto.getInternshipDomain());
        record.put("mentorName", dto.getMentorName());
        record.put("mentorEmail", dto.getMentorEmail());
        record.put("joiningDate", dto.getJoiningDate());
        record.put("completionDate", dto.getCompletionDate());
        record.put("officeStartTime", dto.getOfficeStartTime() != null ? dto.getOfficeStartTime() : "09:00");
        record.put("officeEndTime", dto.getOfficeEndTime() != null ? dto.getOfficeEndTime() : "17:00");
        record.put("status", dto.getStatus());
        record.put("createdAt", timestamp);
        record.put("createdBy", hodUid);

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("internships").document(newUid).set(record).get();
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore update unreachable during application creation: {}", e.getMessage());
        }

        devSimulatedApplications.put(newUid, record);

        // INSTANT AUTOMATION: Instantly evaluate Ongoing transition for new applications
        if (statusService != null && attendancePopupJob != null && engagementPopupJob != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    statusService.runStatusTransitionCheck();
                    attendancePopupJob.generateDailyAttendanceChecks();
                    engagementPopupJob.generateDailyEngagementSchedules();
                } catch (Exception ex) {
                    log.error("Failed to execute instant automation", ex);
                }
            });
        }

        Map<String, Object> response = new HashMap<>(record);
        response.put("message", "Application created");
        return response;
    }

    public Map<String, Object> updateApplication(String uid, SecurityValidationDtos.ApplicationCrudDto dto, String hodUid) {
        long timestamp = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", dto.getFullName());
        updates.put("collegeEmail", dto.getCollegeEmail());
        updates.put("branch", dto.getBranch());
        updates.put("internshipDomain", dto.getInternshipDomain());
        updates.put("mentorName", dto.getMentorName());
        updates.put("mentorEmail", dto.getMentorEmail());
        updates.put("joiningDate", dto.getJoiningDate());
        updates.put("completionDate", dto.getCompletionDate());
        updates.put("officeStartTime", dto.getOfficeStartTime() != null ? dto.getOfficeStartTime() : "09:00");
        updates.put("officeEndTime", dto.getOfficeEndTime() != null ? dto.getOfficeEndTime() : "17:00");
        updates.put("status", dto.getStatus());
        updates.put("updatedAt", timestamp);
        updates.put("updatedBy", hodUid);

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("internships").document(uid).update(updates).get();
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore update unreachable during application update for {}: {}", uid, e.getMessage());
        }

        if (devSimulatedApplications.containsKey(uid)) {
            Map<String, Object> record = devSimulatedApplications.get(uid);
            record.putAll(updates);
        }

        // INSTANT AUTOMATION: Instantly evaluate Ongoing transition for updated applications
        if (statusService != null && attendancePopupJob != null && engagementPopupJob != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    statusService.runStatusTransitionCheck();
                    attendancePopupJob.generateDailyAttendanceChecks();
                    engagementPopupJob.generateDailyEngagementSchedules();
                } catch (Exception ex) {
                    log.error("Failed to execute instant automation", ex);
                }
            });
        }

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("message", "Application updated");
        return response;
    }

    public Map<String, Object> deleteApplication(String uid, String hodUid) {
        log.info("HOD [{}] initiating complete wipe for student [{}]", hodUid, uid);
        wipeStudentDataEntirely(uid);
        devSimulatedApplications.remove(uid);

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("message", "Application deleted");
        return response;
    }

    private void wipeStudentDataEntirely(String uid) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                // Delete direct doc matches
                db.collection("users").document(uid).delete();
                db.collection("internships").document(uid).delete();
                db.collection("completion_summaries").document(uid).delete();

                String[] collections = {
                    "attendance", "daily_status", "diaries", "suspicious_diaries",
                    "warnings", "tests", "daily_engagement_schedules", "mentor_reviews",
                    "test_results", "quotas"
                };

                for (String col : collections) {
                    try {
                        java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> docsUid = 
                            db.collection(col).whereEqualTo("uid", uid).get().get().getDocuments();
                        for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docsUid) {
                            doc.getReference().delete();
                        }
                        
                        java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> docsStudentId = 
                            db.collection(col).whereEqualTo("studentId", uid).get().get().getDocuments();
                        for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docsStudentId) {
                            doc.getReference().delete();
                        }
                        
                        java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> docsStudentUid = 
                            db.collection(col).whereEqualTo("studentUid", uid).get().get().getDocuments();
                        for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docsStudentUid) {
                            doc.getReference().delete();
                        }
                    } catch (Exception e) {}
                }

                try {
                    db.collection("popups").document(uid).delete();
                    java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> pendingPopups = 
                        db.collection("popups").document(uid).collection("pending").get().get().getDocuments();
                    for (com.google.cloud.firestore.QueryDocumentSnapshot doc : pendingPopups) {
                        doc.getReference().delete();
                    }
                } catch (Exception e) {}
            }
        } catch (Exception e) {
            log.warn("Failed to delete Firestore documents for {}: {}", uid, e.getMessage());
        }

        try {
            com.google.cloud.storage.Bucket bucket = com.google.firebase.cloud.StorageClient.getInstance().bucket();
            if (bucket != null) {
                for (com.google.cloud.storage.Blob blob : bucket.list(com.google.cloud.storage.Storage.BlobListOption.prefix("documents/" + uid + "/")).iterateAll()) {
                    blob.delete();
                }
                for (com.google.cloud.storage.Blob blob : bucket.list(com.google.cloud.storage.Storage.BlobListOption.prefix("reference-photos/" + uid)).iterateAll()) {
                    blob.delete();
                }
            }
        } catch (Exception e) {}

        try {
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            if (auth != null) {
                auth.deleteUser(uid);
            }
        } catch (Exception e) {}
    }

    @jakarta.annotation.PostConstruct
    public Map<String, Object> cleanupOrphanedData() {
        Map<String, Object> report = new HashMap<>();
        int deletedCount = 0;
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db == null) return report;

            // 1. Gather all valid active student UIDs
            java.util.Set<String> validUids = new java.util.HashSet<>();
            java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> activeInterns = db.collection("internships").get().get().getDocuments();
            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : activeInterns) {
                validUids.add(doc.getId());
            }

            String[] collections = {
                "attendance", "daily_status", "diaries", "suspicious_diaries",
                "warnings", "tests", "daily_engagement_schedules", "mentor_reviews",
                "test_results", "quotas", "completion_summaries"
            };

            for (String col : collections) {
                try {
                    java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> docs = db.collection(col).get().get().getDocuments();
                    for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docs) {
                        String uid = null;
                        if (doc.contains("uid")) uid = doc.getString("uid");
                        else if (doc.contains("studentId")) uid = doc.getString("studentId");
                        else if (doc.contains("studentUid")) uid = doc.getString("studentUid");
                        else uid = doc.getId(); // Fallback to document ID itself if it matches a UID

                        // For daily_engagement_schedules, ID is like {uid}_{date}
                        if (uid != null && uid.contains("_")) {
                            uid = uid.split("_")[0];
                        }

                        if (uid != null && !validUids.contains(uid)) {
                            // Wipe the document
                            doc.getReference().delete();
                            deletedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed scanning collection {} during orphan cleanup: {}", col, e.getMessage());
                }
            }

            // Specialized cleanup for popups using listDocuments to catch deleted parents with orphaned subcollections
            try {
                Iterable<com.google.cloud.firestore.DocumentReference> popupRefs = db.collection("popups").listDocuments();
                for (com.google.cloud.firestore.DocumentReference popupRef : popupRefs) {
                    if (!validUids.contains(popupRef.getId())) {
                        try {
                            java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> pending = popupRef.collection("pending").get().get().getDocuments();
                            for (com.google.cloud.firestore.QueryDocumentSnapshot p : pending) {
                                p.getReference().delete();
                                deletedCount++;
                            }
                        } catch (Exception ignore) {}
                        popupRef.delete();
                        deletedCount++;
                    }
                }
            } catch (Exception e) {}

            // Cleanup isolated student users
            try {
                java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> userDocs = db.collection("users").get().get().getDocuments();
                for (com.google.cloud.firestore.QueryDocumentSnapshot doc : userDocs) {
                    String role = doc.getString("role");
                    if (role != null && "student".equalsIgnoreCase(role)) {
                        if (!validUids.contains(doc.getId())) {
                            doc.getReference().delete();
                            deletedCount++;
                        }
                    }
                }
            } catch (Exception e) {}

            // Cleanup test questions (format: {uid}_WEEKLY_BANK)
            try {
                java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> tqDocs = db.collection("test_questions").get().get().getDocuments();
                for (com.google.cloud.firestore.QueryDocumentSnapshot doc : tqDocs) {
                    String id = doc.getId();
                    if (id.contains("_")) {
                        String uid = id.split("_")[0];
                        if (!validUids.contains(uid)) {
                            doc.getReference().delete();
                            deletedCount++;
                        }
                    }
                }
            } catch (Exception e) {}

            // Truncate non-student-specific logs/emails as requested for clean slate
            try {
                java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> mailDocs = db.collection("mail").get().get().getDocuments();
                for (com.google.cloud.firestore.QueryDocumentSnapshot doc : mailDocs) {
                    doc.getReference().delete();
                    deletedCount++;
                }
            } catch (Exception e) {}

            try {
                java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> logDocs = db.collection("system_logs").get().get().getDocuments();
                for (com.google.cloud.firestore.QueryDocumentSnapshot doc : logDocs) {
                    doc.getReference().delete();
                    deletedCount++;
                }
            } catch (Exception e) {}

        } catch (Exception e) {
            log.error("Failed to execute retroactive cleanup: {}", e.getMessage());
        }
        log.info("Retroactive automated orphan cleanup complete. Total documents wiped: {}", deletedCount);
        report.put("success", true);
        report.put("orphanedDocumentsDeleted", deletedCount);
        return report;
    }

    /**
     * Helper invoked by StatusService during scheduled or manual cron executions to update simulation records.
     */
    public int runDevSimulationStatusCheck(LocalDate today, long nowMs) {
        int count = 0;
        for (Map<String, Object> app : devSimulatedApplications.values()) {
            if ("Approved".equalsIgnoreCase(String.valueOf(app.get("status")))) {
                String joiningDateStr = (String) app.get("joiningDate");
                if (joiningDateStr != null && !joiningDateStr.trim().isEmpty()) {
                    try {
                        LocalDate joinDate = LocalDate.parse(joiningDateStr.trim());
                        
                        boolean shouldTransition = false;
                        if (joinDate.isBefore(today)) {
                            shouldTransition = true;
                        } else if (joinDate.isEqual(today)) {
                            String officeStartTimeStr = (String) app.get("officeStartTime");
                            if (officeStartTimeStr == null || officeStartTimeStr.isEmpty()) {
                                officeStartTimeStr = "09:00";
                            }
                            java.time.LocalTime officeStartTime = java.time.LocalTime.parse(officeStartTimeStr);
                            java.time.LocalTime currentTime = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
                            if (!currentTime.isBefore(officeStartTime)) {
                                shouldTransition = true;
                            }
                        }
                        
                        if (shouldTransition) {
                            app.put("status", "Ongoing");
                            app.put("ongoingSince", nowMs);
                            log.info("Audit Dev Transition: HOD simulation record [{}] transitioned from Approved to Ongoing", app.get("uid"));
                            count++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return count;
    }

    public Map<String, Object> getDevRecordIfPresent(String uid) {
        return devSimulatedApplications.get(uid);
    }

    private String formatStatusString(String upper) {
        switch (upper) {
            case "APPLIED": return "Applied";
            case "APPROVED": return "Approved";
            case "ONGOING": return "Ongoing";
            case "COMPLETED": return "Completed";
            case "REJECTED": return "Rejected";
            default: return upper;
        }
    }

    private List<Map<String, Object>> getDevRecordsFiltered(String filter) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> app : devSimulatedApplications.values()) {
            String st = String.valueOf(app.getOrDefault("status", "Applied")).toUpperCase();
            if ("ALL".equals(filter) || st.equals(filter)) {
                result.add(app);
            }
        }
        return result;
    }

    public String getMentorNameByUid(String uid) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                DocumentSnapshot doc = db.collection("users").document(uid).get().get();
                if (doc.exists() && doc.getString("fullName") != null) {
                    return doc.getString("fullName");
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch mentor name for uid {}: {}", uid, e.getMessage());
        }
        return uid; // Fallback to UID if name not found
    }

    public List<Map<String, Object>> getFacultyMentors() {
        List<Map<String, Object>> mentors = new ArrayList<>();
        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                // Query both lowercase and uppercase to be safe
                ApiFuture<QuerySnapshot> future = db.collection("users").whereIn("role", java.util.Arrays.asList("MENTOR", "mentor")).get();
                List<? extends DocumentSnapshot> documents = future.get().getDocuments();
                // Extract identifiers for batch Firebase Auth verification
                List<com.google.firebase.auth.UserIdentifier> identifiers = new ArrayList<>();
                for (DocumentSnapshot doc : documents) {
                    identifiers.add(new com.google.firebase.auth.UidIdentifier(doc.getId()));
                }

                java.util.Set<String> activeUids = new java.util.HashSet<>();
                if (!identifiers.isEmpty()) {
                    try {
                        com.google.firebase.auth.GetUsersResult result = com.google.firebase.auth.FirebaseAuth.getInstance().getUsersAsync(identifiers).get();
                        for (com.google.firebase.auth.UserRecord record : result.getUsers()) {
                            activeUids.add(record.getUid());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to batch fetch Auth users. Falling back to accepting all DB records. Error: {}", e.getMessage());
                        // Fallback: accept all to prevent UI crash
                        for (DocumentSnapshot doc : documents) activeUids.add(doc.getId());
                    }
                }

                for (DocumentSnapshot doc : documents) {
                    if (activeUids.contains(doc.getId())) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            Map<String, Object> mentor = new HashMap<>();
                            mentor.put("uid", doc.getId());
                            mentor.put("fullName", data.get("fullName"));
                            mentor.put("email", data.get("collegeEmail"));
                            mentor.put("department", data.get("department"));
                            mentors.add(mentor);
                        }
                    } else {
                        log.warn("Mentor {} not found in Auth. Cleaning up automatically.", doc.getId());
                        // Asynchronously delete the orphaned record from Firestore
                        java.util.concurrent.CompletableFuture.runAsync(() -> doc.getReference().delete());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query faculty mentors from remote database.", e);
        }

        return mentors;
    }
}
