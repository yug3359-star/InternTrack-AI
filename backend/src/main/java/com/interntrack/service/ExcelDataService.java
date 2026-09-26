package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelDataService {

    private static final Logger log = LoggerFactory.getLogger(ExcelDataService.class);

    public List<Map<String, Object>> getExcelData(String branch, String statusFilter) {
        List<Map<String, Object>> studentDataList = new ArrayList<>();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db == null) {
                log.error("Firestore offline. Cannot fetch Excel data.");
                return studentDataList;
            }

            Query query = db.collection("internships");
            if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                query = query.whereEqualTo("status", formatStatusString(statusFilter));
            }
            // If statusFilter is empty or ALL, we don't apply any filter and just fetch everything

            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents = (List<QueryDocumentSnapshot>) future.get().getDocuments();

            for (DocumentSnapshot doc : documents) {
                Map<String, Object> internData = doc.getData();
                if (internData == null) continue;

                String uid = doc.getId();

                DocumentSnapshot userDoc = db.collection("users").document(uid).get().get();
                if (!userDoc.exists() || userDoc.getData() == null) continue;

                String userBranch = userDoc.getString("branch");
                if (branch != null && !branch.isEmpty() && !branch.equalsIgnoreCase(userBranch)) {
                    continue; // Skip if it doesn't match the requested branch
                }

                Map<String, Object> record = new HashMap<>();
                record.put("uid", uid);
                record.put("fullName", userDoc.getString("fullName"));
                
                String regNo = userDoc.getString("registrationNumber");
                if (regNo == null || regNo.isEmpty()) regNo = userDoc.getString("enrollmentNo");
                record.put("registrationNumber", regNo);
                
                record.put("rollNo", userDoc.getString("rollNo"));
                record.put("section", userDoc.getString("section"));
                record.put("branch", userBranch);
                
                Object semesterObj = userDoc.get("semester");
                record.put("semester", semesterObj != null ? String.valueOf(semesterObj) : "N/A");
                
                record.put("mobileNumber", userDoc.getString("mobileNumber"));
                record.put("collegeEmail", userDoc.getString("collegeEmail"));

                // Internship Info
                record.put("modeOfInternship", internData.getOrDefault("modeOfInternship", "N/A"));
                record.put("companyName", internData.getOrDefault("companyName", "N/A"));
                record.put("city", internData.getOrDefault("companyAddress", "N/A"));
                record.put("internshipStipend", internData.getOrDefault("internshipStipend", "N/A"));

                String joiningDateStr = (String) internData.get("joiningDate");
                String completionDateStr = (String) internData.get("completionDate");
                record.put("joiningDate", joiningDateStr);
                record.put("completionDate", completionDateStr);

                // Documents
                record.put("referencePhotoUrl", internData.getOrDefault("referencePhotoUrl", "N/A"));
                record.put("offerLetterUrl", internData.getOrDefault("offerLetterUrl", "N/A"));
                record.put("approvalLetterUrl", internData.getOrDefault("approvalLetterUrl", "N/A"));

                // Total Days
                long totalDays = calculateTotalDays(joiningDateStr, completionDateStr);
                record.put("totalDays", totalDays);

                // Attendance
                String status = (String) internData.get("status");
                double attendancePct = 0.0;
                
                if ("Completed".equalsIgnoreCase(status)) {
                    DocumentSnapshot compDoc = db.collection("completion_summaries").document(uid).get().get();
                    if (compDoc.exists() && compDoc.contains("attendancePercentage")) {
                        Double pct = compDoc.getDouble("attendancePercentage");
                        if (pct != null) attendancePct = pct;
                    }
                } else {
                    // Ongoing - calculate dynamically
                    attendancePct = calculateOngoingAttendance(db, uid, joiningDateStr);
                }
                record.put("attendancePercentage", String.format("%.1f", attendancePct));

                studentDataList.add(record);
            }
        } catch (Exception e) {
            log.error("Failed to aggregate data for Excel export: {}", e.getMessage(), e);
        }

        // Removed dev simulation fallback

        return studentDataList;
    }

    private long calculateTotalDays(String start, String end) {
        if (start == null || end == null) return 0;
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            return days > 0 ? days : 0;
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private double calculateOngoingAttendance(Firestore db, String uid, String joiningDateStr) throws Exception {
        if (joiningDateStr == null) return 0.0;
        LocalDate joinDate;
        try {
            joinDate = LocalDate.parse(joiningDateStr);
        } catch (DateTimeParseException e) {
            return 0.0;
        }

        LocalDate today = LocalDate.now();
        if (today.isBefore(joinDate)) return 0.0;

        long workingDaysElapsed = ChronoUnit.DAYS.between(joinDate, today);
        if (workingDaysElapsed <= 0) workingDaysElapsed = 1;

        // Count present/excused days
        QuerySnapshot attSnapshot = db.collection("attendance")
                .whereGreaterThanOrEqualTo(com.google.cloud.firestore.FieldPath.documentId(), uid + "_" + joinDate.toString())
                .whereLessThanOrEqualTo(com.google.cloud.firestore.FieldPath.documentId(), uid + "_" + today.toString() + "\uf8ff")
                .get().get();

        long presentCount = 0;
        for (DocumentSnapshot doc : attSnapshot.getDocuments()) {
            if (doc.getId().startsWith(uid + "_")) {
                String status = doc.getString("status");
                if ("PRESENT".equalsIgnoreCase(status) || "EXCUSED_MEETING".equalsIgnoreCase(status)) {
                    presentCount++;
                }
            }
        }

        return ((double) presentCount / (double) workingDaysElapsed) * 100.0;
    }

    private String formatStatusString(String status) {
        if (status == null || status.isEmpty()) return "Ongoing";
        String lower = status.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }
}
