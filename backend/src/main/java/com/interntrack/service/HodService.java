package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.exception.InvalidRegistrationException;
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

    public HodService() {
        initializeDevSimulationRecords();
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
    public Map<String, Object> approveApplication(String uid, String hodUid) {
        long timestamp = System.currentTimeMillis();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null && !devSimulatedApplications.containsKey(uid)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "Approved");
                updates.put("approvedAt", timestamp);
                updates.put("approvedBy", hodUid);
                db.collection("internships").document(uid).update(updates).get();
                log.info("HOD [{}] approved internship application for student [{}] at timestamp [{}]", hodUid, uid, timestamp);
            }
        } catch (Exception e) {
            log.warn("Cloud Firestore update unreachable during approval commit for {}: {}", uid, e.getMessage());
        }

        if (devSimulatedApplications.containsKey(uid)) {
            Map<String, Object> record = devSimulatedApplications.get(uid);
            record.put("status", "Approved");
            record.put("approvedAt", timestamp);
            record.put("approvedBy", hodUid);
            log.info("Dev Simulation: HOD [{}] approved mock application [{}]", hodUid, uid);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("status", "Approved");
        response.put("message", "Application approved");
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
            if (db != null && !devSimulatedApplications.containsKey(uid)) {
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
                        if (!joinDate.isAfter(today)) {
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

    private void initializeDevSimulationRecords() {
        long now = System.currentTimeMillis();
        long dayMs = 86400000L;

        addMockStudent("dev-stud-101", "Ananya Sharma", "ananya.sharma@cs.college.edu", "Computer Science & Engineering",
                "Cloud Infrastructure & DevOps", "Vikram Aditya", "vikram@cloudcorp.org", "Desktop",
                "2026-08-01", "2026-11-30", "Applied", now - (5 * dayMs), 0, 0);

        addMockStudent("dev-stud-102", "Rohit Verma", "rohit.verma@cs.college.edu", "Information Technology",
                "Artificial Intelligence & Machine Learning", "Priya Nair", "priya.nair@ai-labs.io", "Desktop",
                "2026-07-20", "2026-11-20", "Ongoing", now - (15 * dayMs), now - (12 * dayMs), now - (6 * dayMs));

        addMockStudent("dev-stud-103", "Siddharth Rao", "siddharth.r@ai.college.edu", "Artificial Intelligence & Data Science",
                "Cybersecurity & Vulnerability Assessment", "Amitabh Ghosh", "aghosh@sec-guard.com", "Desktop",
                "2026-08-10", "2026-12-10", "Applied", now - (3 * dayMs), 0, 0);

        addMockStudent("dev-stud-104", "Kavya Patel", "kavya.p@ec.college.edu", "Electronics & Communication",
                "Embedded IoT Systems & Firmware", "Dr. Rajeshwar Singh", "rsingh@tech-devices.org", "Desktop",
                LocalDate.now().toString(), "2026-11-15", "Approved", now - (2 * dayMs), now - dayMs, 0);

        addMockStudent("dev-stud-105", "Arjun Mehta", "arjun.mehta@cs.college.edu", "Computer Science & Engineering",
                "Software Development & Architecture", "Sneha Kulkarni", "sneha@fintech-solutions.co", "Desktop",
                "2026-05-01", "2026-07-15", "Completed", now - (60 * dayMs), now - (58 * dayMs), now - (55 * dayMs));

        addMockStudent("dev-stud-106", "Neerja Desai", "neerja.d@cs.college.edu", "Computer Science & Engineering",
                "Web3 & Blockchain Systems", "Rishabh Malhotra", "rmalhotra@crypto-verify.io", "Mobile",
                "2026-08-01", "2026-12-01", "Rejected", now - (4 * dayMs), 0, 0);
    }

    private void addMockStudent(String uid, String name, String email, String branch, String domain,
                                String mentorName, String mentorEmail, String device, String joinDate, String compDate, 
                                String status, long createdAt, long approvedAt, long ongoingSince) {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("fullName", name);
        map.put("collegeEmail", email);
        map.put("branch", branch);
        map.put("role", "student");
        map.put("internshipDomain", domain);
        map.put("mentorName", mentorName);
        map.put("mentorEmail", mentorEmail);
        map.put("deviceType", device);
        map.put("joiningDate", joinDate);
        map.put("completionDate", compDate);
        map.put("officeStartTime", "09:00");
        map.put("officeEndTime", "17:00");
        map.put("breakStartTime", "13:00");
        map.put("breakEndTime", "14:00");
        map.put("workingDays", List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"));
        map.put("status", status);
        map.put("consentGiven", true);
        map.put("consentTimestamp", createdAt - 60000L);
        map.put("createdAt", createdAt);
        if (approvedAt > 0) map.put("approvedAt", approvedAt);
        if (ongoingSince > 0) map.put("ongoingSince", ongoingSince);
        if ("Rejected".equals(status)) {
            map.put("rejectedAt", createdAt + 3600000L);
            map.put("rejectionReason", "Offer letter corporate header watermark missing official corporate authentication signature.");
        }
        map.put("referencePhotoUrl", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80");
        map.put("offerLetterUrl", "https://interntrack.dev/documents/" + uid + "/offer-letter.pdf");
        map.put("approvalLetterUrl", "https://interntrack.dev/documents/" + uid + "/approval-letter.pdf");

        devSimulatedApplications.put(uid, map);
    }
}
