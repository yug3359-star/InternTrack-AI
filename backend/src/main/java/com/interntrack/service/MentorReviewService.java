package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.interntrack.exception.InvalidRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service managing faculty mentor evaluations for borderline facial recognition check-ins (40% - 75% match score).
 * Persists all records directly to Firestore 'mentor_reviews' collection to ensure auditable cloud compliance.
 */
@Service
public class MentorReviewService {

    private static final Logger log = LoggerFactory.getLogger(MentorReviewService.class);
    private final AtomicLong reviewIdCounter = new AtomicLong(1001L);

    @Autowired(required = false)
    private Firestore firestore;

    // In-memory borderline review cache for fallback developer runs when Firebase is inactive
    private final Map<String, Map<String, Object>> borderlineReviewRegistry = new ConcurrentHashMap<>();

    public MentorReviewService() {
        // initializeSampleBorderlineReviews(); // Removed fake data population
    }

    /**
     * Registers a new borderline biometric scan in Firestore mentor_reviews collection.
     */
    public String createBorderlineReview(String studentUid, double similarityScore, String checkInPhotoUrl, String referencePhotoUrl) {
        String reviewId = "REV-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
        
        String studentName = "Unknown Student";
        String domain = "Internship";
        
        if (firestore != null) {
            try {
                DocumentSnapshot internDoc = firestore.collection("internships").document(studentUid).get().get();
                if (internDoc.exists()) {
                    studentName = internDoc.getString("fullName");
                    domain = internDoc.getString("internshipDomain");
                }
            } catch (Exception e) {
                log.warn("Could not fetch student data for {}: {}", studentUid, e.getMessage());
            }
        }
        
        Map<String, Object> record = new HashMap<>();
        record.put("reviewId", reviewId);
        record.put("studentUid", studentUid);
        record.put("studentName", studentName);
        record.put("internshipDomain", domain);
        record.put("similarityScore", Math.round(similarityScore * 10.0) / 10.0);
        record.put("checkInPhotoUrl", checkInPhotoUrl != null ? checkInPhotoUrl : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200");
        record.put("referencePhotoUrl", referencePhotoUrl != null ? referencePhotoUrl : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200");
        record.put("status", "PENDING_REVIEW");
        record.put("timestamp", System.currentTimeMillis());

        borderlineReviewRegistry.put(reviewId, record);

        if (firestore != null) {
            try {
                firestore.collection("mentor_reviews").document(reviewId).set(record);
                log.info("Persisted Borderline Biometric Review [ID: {}] to Firestore mentor_reviews collection for student [{}] with similarity [{}%]", reviewId, studentUid, similarityScore);
            } catch (Exception e) {
                log.error("Failed saving borderline review to Firestore: {}", e.getMessage(), e);
            }
        } else {
            log.info("Registered local Borderline Biometric Review [ID: {}] for student [{}] with similarity [{}%]", reviewId, studentUid, similarityScore);
        }

        return reviewId;
    }

    public List<Map<String, Object>> getPendingReviews(String mentorName, boolean isHod) {
        List<Map<String, Object>> liveList = new ArrayList<>();
        if (firestore != null) {
            try {
                Query query = firestore.collection("mentor_reviews").whereEqualTo("status", "PENDING_REVIEW");
                ApiFuture<QuerySnapshot> future = query.get();
                List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                
                for (DocumentSnapshot doc : docs) {
                    Map<String, Object> data = doc.getData();
                    if (data != null) {
                        String studentUid = (String) data.get("studentUid");
                        if (studentUid != null) {
                            if (isHod) {
                                liveList.add(data);
                            } else {
                                DocumentSnapshot internDoc = firestore.collection("internships").document(studentUid).get().get();
                                if (internDoc.exists() && mentorName.equals(internDoc.getString("collegeMentor"))) {
                                    liveList.add(data);
                                }
                            }
                        }
                    }
                }
                liveList.sort((a, b) -> Long.compare(
                        ((Number) b.getOrDefault("timestamp", 0L)).longValue(),
                        ((Number) a.getOrDefault("timestamp", 0L)).longValue()
                ));
            } catch (Exception e) {
                log.error("Error querying Firestore mentor_reviews: {}", e.getMessage());
            }
        }
        return liveList;
    }

    public Map<String, Object> resolveReview(String reviewId, String action, String mentorUid, String notes) {
        String cleanAction = "APPROVE".equalsIgnoreCase(action) ? "APPROVED" : "REJECTED";
        long now = System.currentTimeMillis();
        String resolver = mentorUid != null ? mentorUid : "FACULTY-MENTOR-01";

        if (firestore != null) {
            try {
                DocumentReference docRef = firestore.collection("mentor_reviews").document(reviewId);
                DocumentSnapshot doc = docRef.get().get();
                if (doc.exists()) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", cleanAction);
                    updates.put("resolvedBy", resolver);
                    updates.put("resolvedAt", now);
                    if (notes != null && !notes.trim().isEmpty()) {
                        updates.put("mentorNotes", notes.trim());
                    }
                    docRef.update(updates).get();
                    log.info("Mentor [{}] updated Firestore review [{}] to status: [{}]", resolver, reviewId, cleanAction);
                    Map<String, Object> updatedData = new HashMap<>(doc.getData());
                    updatedData.putAll(updates);
                    return updatedData;
                }
            } catch (Exception e) {
                log.warn("Could not update review in Firestore: {}", e.getMessage());
            }
        }

        throw new InvalidRegistrationException("Target borderline review record not found or server offline: " + reviewId);
    }
}
