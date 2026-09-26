package com.interntrack.controller;

import com.interntrack.dto.SecurityValidationDtos;
import com.interntrack.service.DiaryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private static final Logger log = LoggerFactory.getLogger(DiaryController.class);

    @Autowired
    private DiaryService diaryService;

    /**
     * Submit daily or weekly internship activity diary for automated AI review and topic extraction.
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitDiary(@Valid @RequestBody SecurityValidationDtos.DiarySubmitDto payload) {
        log.info("REST POST request to submit student activity log for candidate UID [{}] on date [{}]", payload.getUid(), payload.getDate());

        String uid = (payload.getUid() != null) ? payload.getUid() : "unknown";
        String date = (payload.getDate() != null) ? payload.getDate() : LocalDate.now().toString();
        String entryText = (payload.getEntryText() != null) ? payload.getEntryText() : "";
        String studentName = (payload.getStudentName() != null) ? payload.getStudentName() : "Unknown Student";
        boolean rejectedDueToFaceMismatch = Boolean.TRUE.equals(payload.getRejectedDueToFaceMismatch());

        try {
            Map<String, Object> result = diaryService.submitDiary(uid, date, entryText, rejectedDueToFaceMismatch, studentName);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            log.error("Error executing diary submit operation: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", "Internal processing fault during diary submission.");
            return ResponseEntity.internalServerError().body(err);
        }
    }

    /**
     * Retrieve chronological log history for student, displaying accepted & rejected badges with audit reasons.
     */
    @GetMapping("/student/{uid}")
    public ResponseEntity<List<Map<String, Object>>> getStudentDiaries(@PathVariable String uid) {
        log.info("REST GET request to query diary ledger timeline for student [{}]", uid);
        List<Map<String, Object>> records = diaryService.getStudentDiaries(uid);
        return ResponseEntity.ok(records);
    }
}
