package com.interntrack.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.interntrack.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hod")
@RequireRole("hod")
public class HodBackfillController {

    @PostMapping("/backfill-student-details/{uid}")
    public ResponseEntity<Map<String, Object>> backfillStudentDetails(
            @PathVariable String uid,
            @RequestBody Map<String, Object> payload) throws Exception {
        
        Firestore db = FirestoreClient.getFirestore();
        if (db == null) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Firestore offline"));
        }
        
        db.collection("users").document(uid).update(
            "rollNo", payload.get("rollNo"),
            "section", payload.get("section"),
            "semester", payload.get("semester"),
            "mobileNumber", payload.get("mobileNumber")
        ).get();
        
        return ResponseEntity.ok(Map.of("message", "Student identity details backfilled successfully"));
    }
}
