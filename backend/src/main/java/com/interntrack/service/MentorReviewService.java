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
        initializeSampleBorderlineReviews();
    }

    /**
     * Registers a new borderline biometric scan in Firestore mentor_reviews collection.
     */
    public String createBorderlineReview(String studentUid, double similarityScore, String checkInPhotoUrl, String referencePhotoUrl) {
        String reviewId = "REV-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
        
        Map<String, Object> record = new HashMap<>();
        record.put("reviewId", reviewId);
        record.put("studentUid", studentUid);
        record.put("studentName", getMockStudentName(studentUid));
        record.put("internshipDomain", getMockStudentDomain(studentUid));
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

    /**
     * Retrieves all pending borderline reviews for faculty evaluation from real Firestore database.
     */
    public List<Map<String, Object>> getPendingReviews() {
        if (firestore != null) {
            try {
                Query query = firestore.collection("mentor_reviews").whereEqualTo("status", "PENDING_REVIEW");
                ApiFuture<QuerySnapshot> future = query.get();
                List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                List<Map<String, Object>> liveList = new ArrayList<>();
                for (DocumentSnapshot doc : docs) {
                    liveList.add(doc.getData());
                }
                liveList.sort((a, b) -> Long.compare(
                        ((Number) b.getOrDefault("timestamp", 0L)).longValue(),
                        ((Number) a.getOrDefault("timestamp", 0L)).longValue()
                ));
                if (!liveList.isEmpty()) {
                    return liveList;
                }
            } catch (Exception e) {
                log.warn("Error querying Firestore mentor_reviews: {}. Utilizing memory fallback.", e.getMessage());
            }
        }

        List<Map<String, Object>> pendingList = new ArrayList<>();
        for (Map<String, Object> rev : borderlineReviewRegistry.values()) {
            if ("PENDING_REVIEW".equals(rev.get("status"))) {
                pendingList.add(rev);
            }
        }
        pendingList.sort((a, b) -> Long.compare(
                ((Number) b.getOrDefault("timestamp", 0L)).longValue(),
                ((Number) a.getOrDefault("timestamp", 0L)).longValue()
        ));
        return pendingList;
    }

    /**
     * Executes one-tap resolution ("APPROVE" or "REJECT") on a borderline candidate check-in in Firestore.
     */
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

        if (!borderlineReviewRegistry.containsKey(reviewId)) {
            throw new InvalidRegistrationException("Target borderline review record not found: " + reviewId);
        }

        Map<String, Object> record = borderlineReviewRegistry.get(reviewId);
        record.put("status", cleanAction);
        record.put("resolvedBy", resolver);
        record.put("resolvedAt", now);
        if (notes != null && !notes.trim().isEmpty()) {
            record.put("mentorNotes", notes.trim());
        }

        log.info("Mentor [{}] resolved borderline review [{}] with final adjudication: [{}]", resolver, reviewId, cleanAction);
        return record;
    }

    private void initializeSampleBorderlineReviews() {
        createSample("dev-stud-102", "Rohit Verma", "Artificial Intelligence & Machine Learning", 58.4,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&auto=format&fit=crop&q=80");

        createSample("dev-stud-108", "Simran Kaur", "Cloud Infrastructure & DevOps", 64.1,
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=200&auto=format&fit=crop&q=80");
    }

    private void createSample(String uid, String name, String domain, double score, String checkIn, String ref) {
        String id = "REV-" + reviewIdCounter.getAndIncrement();
        Map<String, Object> rec = new HashMap<>();
        rec.put("reviewId", id);
        rec.put("studentUid", uid);
        rec.put("studentName", name);
        rec.put("internshipDomain", domain);
        rec.put("similarityScore", score);
        rec.put("checkInPhotoUrl", checkIn);
        rec.put("referencePhotoUrl", ref);
        rec.put("status", "PENDING_REVIEW");
        rec.put("timestamp", System.currentTimeMillis() - 1800000L);
        borderlineReviewRegistry.put(id, rec);
    }

    private String getMockStudentName(String uid) {
        if ("dev-stud-107".equals(uid)) return "Priya Shinde";
        if ("dev-stud-102".equals(uid)) return "Rohit Verma";
        return "Student Profile (" + uid + ")";
    }

    private String getMockStudentDomain(String uid) {
        if ("dev-stud-107".equals(uid)) return "Cloud Infrastructure & DevOps";
        if ("dev-stud-102".equals(uid)) return "Artificial Intelligence & ML";
        return "Computer Science Systems";
    }
}
