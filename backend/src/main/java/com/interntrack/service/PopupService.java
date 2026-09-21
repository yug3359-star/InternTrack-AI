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
        engagementPopupJob.resolvePendingPopup(uid, popupId);

        if (payload.get("simulatedScore") instanceof Number) {
            simulatedScore = ((Number) payload.get("simulatedScore")).doubleValue();
        }

        log.info("Processing engagement popup response for student [{}], action [{}], popupId [{}]", uid, action,
                popupId);

        if ("IN_MEETING".equals(action)) {
            return claimMeetingOverride(uid, popupId);
        }

        // Client-side provided similarity score
        Double similarityScore = (payload.get("similarityScore") instanceof Number)
                ? ((Number) payload.get("similarityScore")).doubleValue()
                : 100.0; // fallback to pass if not provided

        String matchStatus;
        if (similarityScore >= 75.0) {
            matchStatus = "APPROVED";
        } else if (similarityScore >= 40.0) {
            matchStatus = "BORDERLINE";
        } else {
            matchStatus = "REJECTED";
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
