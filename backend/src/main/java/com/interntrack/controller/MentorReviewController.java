package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.MentorReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private MentorReviewService mentorReviewService;

    @Autowired
    private com.interntrack.service.HodService hodService;

    /**
     * Retrieve all borderline biometric scans pending faculty adjudication.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPendingReviews() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        
        String uid = auth.getName();
        boolean isHod = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HOD"));
        String mentorName = hodService.getMentorNameByUid(uid);
        
        List<Map<String, Object>> pending = mentorReviewService.getPendingReviews(mentorName, isHod);
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("reviews", pending);
        resp.put("totalPending", pending.size());
        return ResponseEntity.ok(resp);
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
