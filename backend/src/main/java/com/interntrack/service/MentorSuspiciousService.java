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
    public List<Map<String, Object>> getSuspiciousDiaries(String mentorName, boolean isHod) {
        log.info("Querying suspicious student diary logs for mentor evaluation [{}]", mentorName);
        List<Map<String, Object>> results = new ArrayList<>();

        Firestore firestoreLocal = null;
        try {
            firestoreLocal = com.google.firebase.cloud.FirestoreClient.getFirestore();
        } catch (Exception e) {
            log.error("Failed to get FirestoreClient instance: {}", e.getMessage());
        }

        if (firestoreLocal != null) {
            try {
                List<QueryDocumentSnapshot> docs = firestoreLocal.collection("suspicious_diaries").get().get().getDocuments();
                for (QueryDocumentSnapshot d : docs) {
                    Map<String, Object> data = d.getData();
                    if (data != null) {
                        String studentUid = (String) data.get("uid");
                        if (studentUid != null) {
                            if (isHod) {
                                data.put("id", d.getId());
                                
                                try {
                                    DocumentSnapshot userDoc = firestoreLocal.collection("users").document(studentUid).get().get();
                                    if (userDoc.exists() && userDoc.getString("fullName") != null) {
                                        data.put("studentName", userDoc.getString("fullName"));
                                    }
                                } catch (Exception ignored) {}
                                
                                results.add(data);
                            } else {
                                DocumentSnapshot internDoc = firestoreLocal.collection("internships").document(studentUid).get().get();
                                if (internDoc.exists()) {
                                    String cMentor = internDoc.getString("collegeMentor");
                                    String aMentor = internDoc.getString("assignedMentor");
                                    
                                    // Sanitize inputs by trimming trailing whitespaces that can break matching
                                    String cleanCMentor = cMentor != null ? cMentor.trim() : "";
                                    String cleanAMentor = aMentor != null ? aMentor.trim() : "";
                                    String cleanMentorName = mentorName != null ? mentorName.trim() : "";
                                    
                                    log.info("Checking diary for student {}. Mentor assigned in DB: collegeMentor='{}', assignedMentor='{}'. Mentor logging in: '{}'", studentUid, cleanCMentor, cleanAMentor, cleanMentorName);
                                    
                                    if (cleanMentorName.equalsIgnoreCase(cleanCMentor) || cleanMentorName.equalsIgnoreCase(cleanAMentor)) {
                                        data.put("id", d.getId());
                                        
                                        try {
                                            DocumentSnapshot userDoc = firestoreLocal.collection("users").document(studentUid).get().get();
                                            if (userDoc.exists() && userDoc.getString("fullName") != null) {
                                                data.put("studentName", userDoc.getString("fullName"));
                                            }
                                        } catch (Exception ignored) {}
                                        
                                        results.add(data);
                                    } else {
                                        log.info("Diary skipped. Mentor mismatch.");
                                    }
                                } else {
                                    log.warn("Internship doc not found for student {}", studentUid);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed querying Firestore suspicious_diaries: {}", e.getMessage());
            }
        }

        // Sort descending by flaggedAt or submittedAt timestamp safely
        results.sort((a, b) -> {
            long tA = 0L;
            long tB = 0L;
            
            if (a.get("flaggedAt") instanceof Number) {
                tA = ((Number) a.get("flaggedAt")).longValue();
            } else if (a.get("submittedAt") instanceof Number) {
                tA = ((Number) a.get("submittedAt")).longValue();
            }
            
            if (b.get("flaggedAt") instanceof Number) {
                tB = ((Number) b.get("flaggedAt")).longValue();
            } else if (b.get("submittedAt") instanceof Number) {
                tB = ((Number) b.get("submittedAt")).longValue();
            }
            
            return Long.compare(tB, tA);
        });

        log.info("Successfully fetched {} suspicious diaries for mentor [{}]", results.size(), mentorName);
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

        Firestore firestoreLocal = null;
        try {
            firestoreLocal = com.google.firebase.cloud.FirestoreClient.getFirestore();
        } catch (Exception e) {}

        if ("accept".equalsIgnoreCase(action)) {
            if (firestoreLocal != null) {
                try {
                    DocumentReference sRef = firestoreLocal.collection("suspicious_diaries").document(docId);
                    DocumentSnapshot sDoc = sRef.get().get();
                    if (sDoc.exists()) {
                        Map<String, Object> record = new HashMap<>(sDoc.getData());
                        record.put("status", "accepted");
                        record.put("mentorOverridden", true);
                        record.put("overriddenBy", mentorUid != null ? mentorUid : "Faculty Mentor");
                        record.put("reviewReason", "Faculty Mentor Overrode AI Rejection (Verified compliance by " + (mentorUid != null ? mentorUid : "Faculty") + ")");
                        
                        firestoreLocal.collection("diaries").document(docId).set(record).get();
                        sRef.delete().get();
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
            if (firestoreLocal != null) {
                try {
                    firestoreLocal.collection("suspicious_diaries").document(docId).delete().get();
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
    public int getUnreviewedSuspiciousCount(String mentorName, boolean isHod) {
        Firestore firestoreLocal = null;
        try {
            firestoreLocal = com.google.firebase.cloud.FirestoreClient.getFirestore();
        } catch (Exception e) {}
        
        if (firestoreLocal != null) {
            try {
                // We reuse the list logic to ensure the badge count exactly matches the list count.
                return getSuspiciousDiaries(mentorName, isHod).size();
            } catch (Exception e) {
                log.trace("Fallback count usage due to Firestore exception: {}", e.getMessage());
            }
        }
        return 0;
    }
}
