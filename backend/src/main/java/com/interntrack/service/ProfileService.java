package com.interntrack.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    @Autowired(required = false)
    private Firestore firestore;

    /**
     * Retrieves the profile data for the requested UID.
     * Merges user data with internship data if the user is a student.
     */
    public Map<String, Object> getProfile(String uid) {
        Map<String, Object> profile = new HashMap<>();

        if (firestore == null) {
            log.warn("Firestore not available, returning empty profile");
            return profile;
        }

        try {
            DocumentSnapshot userDoc = firestore.collection("users").document(uid).get().get();
            if (userDoc.exists()) {
                profile.putAll(userDoc.getData());
            }

            // If user is a student, fetch internship summary
            String role = (String) profile.getOrDefault("role", "");
            if ("student".equalsIgnoreCase(role)) {
                DocumentSnapshot internshipDoc = firestore.collection("internships").document(uid).get().get();
                if (internshipDoc.exists()) {
                    Map<String, Object> internshipData = internshipDoc.getData();
                    // Merge only specific read-only fields for the profile view
                    profile.put("mentorName", internshipData.get("mentorName"));
                    profile.put("joiningDate", internshipData.get("joiningDate"));
                    profile.put("completionDate", internshipData.get("completionDate"));
                    profile.put("officeStartTime", internshipData.get("officeStartTime"));
                    profile.put("officeEndTime", internshipData.get("officeEndTime"));
                    profile.put("referencePhotoUrl", internshipData.get("referencePhotoUrl"));
                }
            }
        } catch (Exception e) {
            log.error("Error fetching profile for uid {}: {}", uid, e.getMessage());
        }

        return profile;
    }

    /**
     * Updates editable fields based on role. Reject attempts to modify read-only fields.
     */
    public void updateProfile(String uid, Map<String, Object> updates) {
        if (firestore == null) {
            log.warn("Firestore not available for updateProfile");
            return;
        }

        try {
            DocumentSnapshot userDoc = firestore.collection("users").document(uid).get().get();
            if (!userDoc.exists()) {
                throw new IllegalArgumentException("User not found");
            }

            Map<String, Object> userData = userDoc.getData();
            String role = (String) userData.getOrDefault("role", "");
            
            // Check for restricted fields in the updates map
            String[] restrictedFields = {"collegeEmail", "branch", "role", "rollNo", "enrollmentNo", "internshipDomain", "referencePhotoUrl", "mentorName"};
            for (String field : restrictedFields) {
                if (updates.containsKey(field)) {
                    log.warn("Attempt to update restricted field [{}] by user [{}]", field, uid);
                    throw new IllegalArgumentException("Cannot update read-only field: " + field);
                }
            }

            Map<String, Object> allowedUpdates = new HashMap<>();
            
            // All roles can update fullName
            if (updates.containsKey("fullName")) {
                allowedUpdates.put("fullName", updates.get("fullName"));
            }

            // HOD can update department
            if ("hod".equalsIgnoreCase(role)) {
                if (updates.containsKey("department")) {
                    allowedUpdates.put("department", updates.get("department"));
                }
            } else {
                if (updates.containsKey("department")) {
                     throw new IllegalArgumentException("Only HOD can update department field");
                }
            }

            if (!allowedUpdates.isEmpty()) {
                firestore.collection("users").document(uid).update(allowedUpdates).get();
                log.info("Profile updated successfully for uid {}", uid);
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating profile for uid {}: {}", uid, e.getMessage());
            throw new RuntimeException("Internal server error while updating profile");
        }
    }

    /**
     * Creates a request for a photo update by a student.
     */
    public void requestPhotoUpdate(String uid) {
        if (firestore == null) {
            log.warn("Firestore not available for requestPhotoUpdate");
            return;
        }

        try {
            DocumentSnapshot userDoc = firestore.collection("users").document(uid).get().get();
            if (!userDoc.exists()) {
                throw new IllegalArgumentException("User not found");
            }

            String fullName = (String) userDoc.getData().get("fullName");

            Map<String, Object> request = new HashMap<>();
            request.put("uid", uid);
            request.put("fullName", fullName);
            request.put("timestamp", System.currentTimeMillis());
            request.put("status", "PENDING_REVIEW");

            String requestId = "PHOTO-REQ-" + UUID.randomUUID().toString();
            firestore.collection("photo_update_requests").document(requestId).set(request).get();
            log.info("Photo update request created for uid {}", uid);

        } catch (Exception e) {
            log.error("Error creating photo update request for uid {}: {}", uid, e.getMessage());
            throw new RuntimeException("Internal server error while creating photo request");
        }
    }
}
