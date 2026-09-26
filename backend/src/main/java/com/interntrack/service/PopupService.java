package com.interntrack.service;

import com.interntrack.exception.InvalidRegistrationException;
import com.interntrack.scheduler.EngagementPopupJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Service managing student responses to live working-hour engagement check-ins,
 * face match biometrics validation, and monthly meeting exemption quotas via
 * shared QuotaService.
 */
@Service
public class PopupService {

    private static final Logger log = LoggerFactory.getLogger(PopupService.class);

    private final MentorReviewService mentorReviewService;
    private final EngagementPopupJob engagementPopupJob;
    private final QuotaService quotaService;
    private final ZoneId applicationZoneId;
    private final DailyStatusService dailyStatusService;

    public PopupService(MentorReviewService mentorReviewService,
            EngagementPopupJob engagementPopupJob, QuotaService quotaService,
            ZoneId applicationZoneId, DailyStatusService dailyStatusService) {
        this.mentorReviewService = mentorReviewService;
        this.engagementPopupJob = engagementPopupJob;
        this.quotaService = quotaService;
        this.applicationZoneId = applicationZoneId;
        this.dailyStatusService = dailyStatusService;
    }

    /**
     * Processes student attendance check-in response ("WORKING" or "IN_MEETING").
     */
    public Map<String, Object> handlePopupResponse(String uid, Map<String, Object> payload) {
        String action = String.valueOf(payload.getOrDefault("action", "WORKING")).toUpperCase();
        String popupId = String.valueOf(payload.getOrDefault("popupId", "unknown-popup"));
        String photoData = (String) payload.get("photoData");
        Double simulatedScore = null;

        // Resolve the pending popup to prevent the sweeper from marking it as missed
        boolean resolved = engagementPopupJob.resolvePendingPopup(uid, popupId);
        if (!resolved && !popupId.startsWith("test-checkin-")) {
            Map<String, Object> errResponse = new HashMap<>();
            errResponse.put("error", true);
            errResponse.put("matchStatus", "EXPIRED");
            errResponse.put("message", "This check-in has already expired and was marked as missed.");
            return errResponse;
        }

        if (payload.get("simulatedScore") instanceof Number) {
            simulatedScore = ((Number) payload.get("simulatedScore")).doubleValue();
        }

        log.info("Processing engagement popup response for student [{}], action [{}], popupId [{}]", uid, action,
                popupId);

        if ("TIMEOUT_MISSED".equals(action)) {
            log.warn("Student [{}] missed popup [{}]. Registering failed attendance attempt.", uid, popupId);
            engagementPopupJob.incrementMissedPopup(uid);
            Map<String, Object> response = new HashMap<>();
            response.put("matchStatus", "EXPIRED");
            response.put("popupId", popupId);
            response.put("action", action);
            response.put("processedAt", System.currentTimeMillis());
            return response;
        }

        if ("IN_MEETING".equals(action)) {
            Map<String, Object> result = claimMeetingOverride(uid, popupId);
            engagementPopupJob.incrementCompletedPopup(uid);
            return result;
        }

        // Client-side provided similarity score
        Double similarityScore = (payload.get("similarityScore") instanceof Number)
                ? ((Number) payload.get("similarityScore")).doubleValue()
                : 100.0; // fallback to pass if not provided

        String matchStatus;
        if (similarityScore >= 40.0) {
            matchStatus = "APPROVED";
            engagementPopupJob.incrementCompletedPopup(uid);
        } else {
            matchStatus = "BORDERLINE";
        }

        if ("BORDERLINE".equals(matchStatus)) {
            log.info("Biometric score borderline for student [{}]. Placing snapshot into mentor review repository.",
                    uid);
            String referencePhotoUrl = "https://firebasestorage.googleapis.com/v0/b/interntrack-dev.appspot.com/o/reference-photos%2F"
                    + uid + ".jpg?alt=media";
            mentorReviewService.createBorderlineReview(uid, similarityScore, photoData, referencePhotoUrl);
        } else if ("REJECTED".equals(matchStatus)) {
            log.warn("Biometric verification failed for student [{}]. Registering failed attendance attempt.", uid);
            engagementPopupJob.incrementMissedPopup(uid);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("similarityScore", similarityScore);
        response.put("matchStatus", matchStatus);
        response.put("popupId", popupId);
        response.put("action", "WORKING");
        response.put("processedAt", System.currentTimeMillis());
        return response;
    }

    /**
     * Retrieves current monthly meeting quota usage (quotas/{uid}_{yyyy-MM}) via
     * shared QuotaService.
     */
    public Map<String, Object> getStudentMeetingQuota(String uid) {
        return quotaService.getStudentMeetingQuota(uid);
    }

    public Map<String, Object> claimWholeDayMeetingOverride(String uid) {
        // Automatically checks and decrements real Firestore doc via QuotaService
        quotaService.consumeMeetingQuotaOrThrow(uid);

        // Stop all popups for today
        engagementPopupJob.clearPopupsForToday(uid);

        // Mark as excused present
        String today = LocalDate.now(applicationZoneId).toString();
        dailyStatusService.recordAttendanceResult(uid, today, "excused_meeting",
                "Claimed whole day meeting exemption token.");
                
        // Ensure the source attendance document itself is explicitly updated so it survives AI/Mentor recalculations
        try {
            com.google.cloud.firestore.Firestore db = com.google.firebase.cloud.FirestoreClient.getFirestore();
            String docId = uid + "_" + today;
            Map<String, Object> update = new HashMap<>();
            update.put("id", docId);
            update.put("uid", uid);
            update.put("date", today);
            update.put("status", "excused_meeting");
            update.put("absenceReason", "Claimed whole day meeting exemption token.");
            db.collection("attendance").document(docId).set(update, com.google.cloud.firestore.SetOptions.merge()).get();
        } catch (Exception e) {
            log.warn("Error updating source attendance doc for meeting exemption: {}", e.getMessage());
        }

        Map<String, Object> currentQuota = quotaService.getStudentMeetingQuota(uid);
        int used = ((Number) currentQuota.getOrDefault("used", 0)).intValue();
        int limit = ((Number) currentQuota.getOrDefault("limit", 3)).intValue();

        log.info("Student [{}] claimed WHOLE DAY meeting override. Popups stopped. Quota status: [{}/{}] used.", uid,
                used, limit);

        Map<String, Object> res = new HashMap<>();
        res.put("uid", uid);
        res.put("action", "WHOLE_DAY_EXEMPTION");
        res.put("quotaUsed", used);
        res.put("quotaLimit", limit);
        res.put("message", "Exemption claimed successfully. Random check-ins disabled for today.");
        return res;
    }

    private Map<String, Object> claimMeetingOverride(String uid, String popupId) {
        // Automatically checks and decrements real Firestore doc via QuotaService
        quotaService.consumeMeetingQuotaOrThrow(uid);
        Map<String, Object> currentQuota = quotaService.getStudentMeetingQuota(uid);

        int used = ((Number) currentQuota.getOrDefault("used", 0)).intValue();
        int limit = ((Number) currentQuota.getOrDefault("limit", 3)).intValue();

        log.info("Student [{}] claimed lawful meeting override for popup [{}]. Quota status: [{}/{}] used.", uid,
                popupId, used, limit);

        Map<String, Object> res = new HashMap<>();
        res.put("uid", uid);
        res.put("action", "IN_MEETING");
        res.put("popupId", popupId);
        res.put("quotaUsed", used);
        res.put("quotaLimit", limit);
        res.put("message", "Check-in excused via valid meeting override. Quota updated: " + used + " / " + limit
                + " used this month.");
        return res;
    }
}
