package com.interntrack.controller;

import com.interntrack.service.MentorSuspiciousService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class TestSuspiciousController {

    @Autowired
    private MentorSuspiciousService mentorSuspiciousService;

    @GetMapping("/api/test-suspicious-dump")
    public Object getSuspiciousDump(@RequestParam String mentorName) {
        try {
            return mentorSuspiciousService.getSuspiciousDiaries(mentorName, false);
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            return sw.toString();
        }
    }

    @GetMapping("/patch-db")
    public String patchDb() {
        try {
            com.google.cloud.firestore.Firestore db = com.google.firebase.cloud.FirestoreClient.getFirestore();
            String docId = "2023_2026-09-24";

            java.util.Map<String, Object> update = new java.util.HashMap<>();
            update.put("status", "excused_meeting");
            update.put("absenceReason", "Claimed whole day meeting exemption token.");
            db.collection("attendance").document(docId).set(update, com.google.cloud.firestore.SetOptions.merge())
                    .get();

            java.util.Map<String, Object> update2 = new java.util.HashMap<>();
            update2.put("status", "excused_meeting");
            update2.put("attendanceStatus", "excused_meeting");
            db.collection("daily_status").document(docId).set(update2, com.google.cloud.firestore.SetOptions.merge())
                    .get();

            return "Patched successfully!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
