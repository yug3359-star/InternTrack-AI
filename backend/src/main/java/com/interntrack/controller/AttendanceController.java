package com.interntrack.controller;

import com.interntrack.scheduler.AttendancePopupJob;
import com.interntrack.scheduler.EngagementPopupJob;
import com.interntrack.service.DailyStatusService;
import com.interntrack.service.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller providing formal attendance check-in endpoints, quota-backed meeting excuses,
 * historical attendance reporting, and live test triggers.
 */
@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AttendancePopupJob attendancePopupJob;
    private final QuotaService quotaService;
    private final DailyStatusService dailyStatusService;
    private final EngagementPopupJob engagementPopupJob;
    private final ZoneId applicationZoneId = ZoneId.of("Asia/Kolkata");

    public AttendanceController(AttendancePopupJob attendancePopupJob, QuotaService quotaService,
                                DailyStatusService dailyStatusService, EngagementPopupJob engagementPopupJob) {
        this.attendancePopupJob = attendancePopupJob;
        this.quotaService = quotaService;
        this.dailyStatusService = dailyStatusService;
        this.engagementPopupJob = engagementPopupJob;
    }

    /**
     * POST /api/attendance/{attendanceId}/respond
     * Body: { "action": "present" | "meeting" }
     */
    @PostMapping("/{attendanceId}/respond")
    public ResponseEntity<Map<String, Object>> respondToAttendance(
            @PathVariable String attendanceId,
            @RequestBody Map<String, Object> payload) {

        String action = String.valueOf(payload.getOrDefault("action", "present")).trim().toLowerCase();
        log.info("Received formal attendance response for doc [{}]: action [{}]", attendanceId, action);

        Map<String, Object> doc = attendancePopupJob.getAttendanceRecord(attendanceId);
        if (doc == null) {
            log.warn("Attendance record [{}] not found in store. Provisioning on-the-fly for response processing.", attendanceId);
            String[] parts = attendanceId.split("_");
            String uid = parts.length > 0 ? parts[0] : "dev-stud-107";
            doc = attendancePopupJob.triggerAttendanceForToday(uid);
        }

        String uid = String.valueOf(doc.getOrDefault("uid", "dev-stud-107"));
        String dateStr = String.valueOf(doc.getOrDefault("date", LocalDate.now(applicationZoneId).format(DATE_FORMATTER)));
        long now = System.currentTimeMillis();

        if ("meeting".equals(action) || "in_meeting".equals(action)) {
            // Attempt consumption from shared meeting quota pool
            boolean quotaConsumed = quotaService.tryConsumeMeetingQuota(uid);
            if (quotaConsumed) {
                doc.put("status", "excused_meeting");
                doc.put("respondedAt", now);
                doc.put("message", "Formal attendance check excused via valid meeting quota pass.");
                attendancePopupJob.saveAttendanceRecord(attendanceId, doc);

                updateDailyCompliance(uid, dateStr);
                return ResponseEntity.ok(doc);
            } else {
                // Quota exhausted -> mark as missed
                log.warn("Student [{}] attempted meeting excuse for daily attendance but monthly allowance is exhausted (0 remaining). Marking missed.", uid);
                doc.put("status", "missed");
                doc.put("respondedAt", now);
                doc.put("error", "Monthly meeting override quota exhausted. Marked missed (counts as absence).");
                attendancePopupJob.saveAttendanceRecord(attendanceId, doc);

                updateDailyCompliance(uid, dateStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(doc);
            }
        } else {
            // Default "present" confirmation
            doc.put("status", "present");
            doc.put("respondedAt", now);
            doc.put("message", "Attendance confirmed present successfully.");
            attendancePopupJob.saveAttendanceRecord(attendanceId, doc);

            updateDailyCompliance(uid, dateStr);
            return ResponseEntity.ok(doc);
        }
    }

    private void updateDailyCompliance(String uid, String dateStr) {
        try {
            int missedPopups = engagementPopupJob != null ? engagementPopupJob.getMissedCountToday(uid) : 0;
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            dailyStatusService.evaluateDailyStatus(uid, date, missedPopups);
        } catch (Exception e) {
            log.error("Failed to sync daily compliance ledger for student {}: {}", uid, e.getMessage());
        }
    }

    /**
     * GET /api/attendance/history/{uid}
     * Returns historical attendance records sorted by date descending along with computed percentages.
     */
    @GetMapping("/history/{uid}")
    public ResponseEntity<Map<String, Object>> getStudentHistory(@PathVariable String uid) {
        List<Map<String, Object>> records = attendancePopupJob.getStudentHistory(uid);

        int totalWorkingDays = records.size();
        int presentDays = 0;
        int excusedDays = 0;
        int missedDays = 0;

        for (Map<String, Object> rec : records) {
            String status = String.valueOf(rec.get("status")).toLowerCase();
            if ("present".equals(status)) presentDays++;
            else if ("excused_meeting".equals(status)) excusedDays++;
            else if ("missed".equals(status)) missedDays++;
        }

        // Attendance formula: ((present + excused) / total) * 100
        double percentage = totalWorkingDays > 0 ? ((double) (presentDays + excusedDays) / totalWorkingDays) * 100.0 : 100.0;
        percentage = Math.round(percentage * 10.0) / 10.0; // 1 decimal place

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("records", records);
        response.put("totalDays", totalWorkingDays);
        response.put("presentDays", presentDays);
        response.put("excusedDays", excusedDays);
        response.put("missedDays", missedDays);
        response.put("attendancePercentage", percentage);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/attendance/trigger-today/{uid}
     * Instant diagnostic trigger for review evaluations without waiting for scheduled cron.
     */
    @PostMapping("/trigger-today/{uid}")
    public ResponseEntity<Map<String, Object>> triggerTodayCheck(@PathVariable String uid) {
        log.info("Manual test trigger invoked: GET /api/attendance/trigger-today/{}", uid);
        Map<String, Object> createdDoc = attendancePopupJob.triggerAttendanceForToday(uid);
        return ResponseEntity.ok(createdDoc);
    }
}
