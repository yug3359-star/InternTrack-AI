package com.interntrack.controller;

import com.interntrack.service.MentorSuspiciousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MentorSuspiciousController {

    private static final Logger log = LoggerFactory.getLogger(MentorSuspiciousController.class);

    @Autowired
    private MentorSuspiciousService mentorSuspiciousService;

    /**
     * Fetch list of suspicious diaries for assigned faculty mentor review.
     */
    @GetMapping({"/api/mentor/suspicious", "/api/hod/suspicious"})
    public ResponseEntity<List<Map<String, Object>>> getSuspiciousQueue(
            @RequestParam(required = false) String mentorEmail) {
        log.info("REST GET request to query flagged suspicious diary queue for faculty review");
        List<Map<String, Object>> queue = mentorSuspiciousService.getSuspiciousDiaries(mentorEmail);
        return ResponseEntity.ok(queue);
    }

    /**
     * Fetch unreviewed count for live sidebar badge rendering.
     */
    @GetMapping({"/api/mentor/suspicious/count", "/api/hod/suspicious/count"})
    public ResponseEntity<Map<String, Object>> getSuspiciousCount() {
        int count = mentorSuspiciousService.getUnreviewedSuspiciousCount();
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", count);
        return ResponseEntity.ok(resp);
    }

    /**
     * Perform decision override on suspicious entry: action = "accept" or "delete".
     */
    @PostMapping({"/api/mentor/suspicious/{id}/override", "/api/hod/suspicious/{id}/override"})
    public ResponseEntity<Map<String, Object>> overrideSuspiciousEntry(
            @PathVariable("id") String docId,
            @RequestBody Map<String, Object> body) {
        log.info("REST POST request to override suspicious diary ID [{}] with payload [{}]", docId, body);

        String action = (String) body.getOrDefault("action", "accept");
        String mentorUid = (String) body.getOrDefault("mentorUid", "Faculty Mentor");

        try {
            Map<String, Object> result = mentorSuspiciousService.handleOverride(docId, action, mentorUid);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            log.error("Internal processing fault during override execution: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", "System exception executing faculty decision override.");
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
