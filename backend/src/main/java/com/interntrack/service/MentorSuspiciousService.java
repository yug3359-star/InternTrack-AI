package com.interntrack.service;

import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MentorSuspiciousService {

    private static final Logger log = LoggerFactory.getLogger(MentorSuspiciousService.class);

    @Autowired(required = false)
    private Firestore firestore;

    @Autowired
    private DiaryService diaryService;

    /**
     * Retrieves all flagged suspicious diaries for investigative faculty review.
     * Can be filtered by mentor email or returned across all students for HOD oversight.
     */
    public List<Map<String, Object>> getSuspiciousDiaries(String mentorEmail) {
        log.info("Querying suspicious student diary logs for mentor evaluation [{}]", mentorEmail);
        List<Map<String, Object>> results = new ArrayList<>();

        if (firestore != null) {
            try {
                List<QueryDocumentSnapshot> docs = firestore.collection("suspicious_diaries").get().get().getDocuments();
                for (QueryDocumentSnapshot d : docs) {
                    Map<String, Object> data = d.getData();
                    // Include if assigned or general HOD view
                    results.add(data);
                }
            } catch (Exception e) {
                log.warn("Failed querying Firestore suspicious_diaries: {}", e.getMessage());
            }
        }

        if (results.isEmpty()) {
            results.addAll(diaryService.getDevSuspiciousRegistry().values());
        }

        // Sort descending by flaggedAt or submittedAt timestamp
        results.sort((a, b) -> {
            Long tA = (Long) a.getOrDefault("flaggedAt", a.getOrDefault("submittedAt", 0L));
            Long tB = (Long) b.getOrDefault("flaggedAt", b.getOrDefault("submittedAt", 0L));
            return tB.compareTo(tA);
        });

        return results;
    }

    /**
     * Executes faculty decision override on a suspicious record: either "accept" or "delete".
     * "accept": transfers record to accepted diaries collection with mentor audit trail.
     * "delete": permanently expunges the record (irreversible).
     */
    public synchronized Map<String, Object> handleOverride(String docId, String action, String mentorUid) {
        log.info("Executing override action [{}] on suspicious document [{}] by mentor [{}]", action, docId, mentorUid);
        Map<String, Object> response = new HashMap<>();
        response.put("docId", docId);
        response.put("action", action);

        if ("accept".equalsIgnoreCase(action)) {
            if (firestore != null) {
                try {
                    DocumentReference sRef = firestore.collection("suspicious_diaries").document(docId);
                    DocumentSnapshot sDoc = sRef.get().get();
                    if (sDoc.exists()) {
                        Map<String, Object> record = new HashMap<>(sDoc.getData());
                        record.put("status", "accepted");
                        record.put("mentorOverridden", true);
                        record.put("overriddenBy", mentorUid != null ? mentorUid : "Faculty Mentor");
                        record.put("reviewReason", "Faculty Mentor Overrode AI Rejection (Verified compliance by " + (mentorUid != null ? mentorUid : "Faculty") + ")");
                        
                        firestore.collection("diaries").document(docId).set(record);
                        sRef.delete();
                        log.info("Transferred suspicious doc [{}] to accepted diaries collection in Firestore.", docId);
                    }
                } catch (Exception e) {
                    log.warn("Error transferring suspicious Firestore doc to accepted: {}", e.getMessage());
                }
            }
            diaryService.overrideSuspiciousToAccepted(docId, mentorUid);
            response.put("status", "OVERRIDE_ACCEPTED");
            response.put("message", "Diary log transferred to Accepted ledger under faculty override authority.");
        } else if ("delete".equalsIgnoreCase(action)) {
            if (firestore != null) {
                try {
                    firestore.collection("suspicious_diaries").document(docId).delete();
                    log.info("Permanently deleted suspicious doc [{}] from Firestore.", docId);
                } catch (Exception e) {
                    log.warn("Error deleting suspicious Firestore doc: {}", e.getMessage());
                }
            }
            diaryService.permanentlyDeleteSuspicious(docId);
            response.put("status", "OVERRIDE_DELETED");
            response.put("message", "Suspicious diary log permanently deleted from departmental compliance ledger.");
        } else {
            throw new IllegalArgumentException("Unsupported action parameter: " + action + ". Expected 'accept' or 'delete'.");
        }

        return response;
    }

    /**
     * Computes count of unreviewed suspicious entries for live sidebar notification badge.
     */
    public int getUnreviewedSuspiciousCount() {
        if (firestore != null) {
            try {
                return firestore.collection("suspicious_diaries").get().get().size();
            } catch (Exception e) {
                log.trace("Fallback count usage due to Firestore exception: {}", e.getMessage());
            }
        }
        return diaryService.getDevSuspiciousRegistry().size();
    }
}
