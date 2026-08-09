package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.MentorReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing one-tap verification queues and adjudication endpoints
 * for faculty mentors assessing borderline (40% - 75%) identity check-in snapshots.
 */
@RestController
@RequestMapping("/api/mentor/borderline-reviews")
@RequireRole({"mentor", "hod"})
public class MentorReviewController {

    private static final Logger log = LoggerFactory.getLogger(MentorReviewController.class);

    private final MentorReviewService mentorReviewService;

    public MentorReviewController(MentorReviewService mentorReviewService) {
        this.mentorReviewService = mentorReviewService;
    }

    /**
     * Fetch all pending borderline check-ins awaiting visual verification.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPendingBorderlineReviews() {
        log.info("REST request to query pending borderline biometric reviews for faculty evaluations");
        List<Map<String, Object>> reviews = mentorReviewService.getPendingReviews();
        Map<String, Object> response = new HashMap<>();
        response.put("reviews", reviews);
        response.put("totalPending", reviews.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Execute one-tap resolution (APPROVE or REJECT) on a specific review record.
     */
    @PatchMapping("/{reviewId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveBorderlineReview(
            @PathVariable String reviewId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Mentor-UID", required = false, defaultValue = "FACULTY-MENTOR-01") String mentorUid) {
        
        String action = String.valueOf(payload.getOrDefault("action", "APPROVE"));
        String notes = (String) payload.get("notes");
        log.info("REST request by mentor [{}] to resolve review [{}] with action [{}]", mentorUid, reviewId, action);
        
        Map<String, Object> updatedRecord = mentorReviewService.resolveReview(reviewId, action, mentorUid, notes);
        return ResponseEntity.ok(updatedRecord);
    }
}
