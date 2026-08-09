package com.interntrack.controller;

import com.interntrack.scheduler.EngagementPopupJob;
import com.interntrack.service.PopupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller exposing real-time engagement check-in response routes, 
 * monthly meeting override quota queries, and on-demand review demonstration triggers.
 */
@RestController
@RequestMapping("/api/popups")
public class PopupController {

    private static final Logger log = LoggerFactory.getLogger(PopupController.class);

    private final PopupService popupService;
    private final EngagementPopupJob engagementPopupJob;

    public PopupController(PopupService popupService, EngagementPopupJob engagementPopupJob) {
        this.popupService = popupService;
        this.engagementPopupJob = engagementPopupJob;
    }

    /**
     * Submit an attendance check-in response ("WORKING" with webcam photo or "IN_MEETING").
     */
    @PostMapping("/{uid}/respond")
    public ResponseEntity<Map<String, Object>> respondToCheckIn(@PathVariable String uid, @RequestBody Map<String, Object> payload) {
        log.info("REST request to submit working engagement response for UID: {}", uid);
        Map<String, Object> result = popupService.handlePopupResponse(uid, payload);
        return ResponseEntity.ok(result);
    }

    /**
     * Claims a whole-day meeting exemption, consuming a quota token and stopping popups for the day.
     */
    @PostMapping("/{uid}/claim-meeting-day")
    public ResponseEntity<Map<String, Object>> claimMeetingDay(@PathVariable String uid) {
        log.info("REST request to claim whole day meeting exemption for UID: {}", uid);
        try {
            Map<String, Object> result = popupService.claimWholeDayMeetingOverride(uid);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Retrieve student's current monthly meeting exemption quota usage.
     */
    @GetMapping("/{uid}/quota")
    public ResponseEntity<Map<String, Object>> getMeetingQuota(@PathVariable String uid) {
        log.info("REST request to fetch meeting quota status for UID: {}", uid);
        Map<String, Object> quota = popupService.getStudentMeetingQuota(uid);
        return ResponseEntity.ok(quota);
    }

    /**
     * On-demand test endpoint to immediately trigger a simulated working hour check-in for presentation purposes.
     */
    @PostMapping("/{uid}/test-trigger")
    public ResponseEntity<Map<String, Object>> triggerTestPopup(@PathVariable String uid) {
        log.info("REST request: Manual testing trigger of 'Are You Working?' check-in for student [{}]", uid);
        Map<String, Object> mockPopup = new HashMap<>();
        mockPopup.put("id", "test-trigger-" + System.currentTimeMillis());
        mockPopup.put("studentUid", uid);
        mockPopup.put("timestamp", System.currentTimeMillis());
        mockPopup.put("deadline", System.currentTimeMillis() + 120000L); // 2 minute response window
        mockPopup.put("message", "Simulated engagement check-in active. Tap to verify attendance.");
        return ResponseEntity.ok(mockPopup);
    }

    /**
     * Diagnostic endpoint to query today's scheduled random working intervals and absence warning counters.
     */
    @GetMapping("/{uid}/status")
    public ResponseEntity<Map<String, Object>> getEngagementStatus(@PathVariable String uid) {
        return ResponseEntity.ok(engagementPopupJob.getStudentEngagementStatus(uid));
    }
}
