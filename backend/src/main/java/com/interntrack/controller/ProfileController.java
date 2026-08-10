package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequireRole({"student", "mentor", "hod"})
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private ProfileService profileService;

    /**
     * Retrieves the profile data for the authenticated user.
     * The UID is extracted from the Spring Security Authentication context.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        String uid = (String) authentication.getPrincipal();
        log.info("Fetching profile for authenticated uid: {}", uid);
        Map<String, Object> profile = profileService.getProfile(uid);
        return ResponseEntity.ok(profile);
    }

    /**
     * Updates editable profile fields for the authenticated user.
     */
    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication authentication,
            @RequestBody Map<String, Object> updates) {
        String uid = (String) authentication.getPrincipal();
        log.info("Updating profile for uid: {}", uid);
        try {
            profileService.updateProfile(uid, updates);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Profile updated successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid profile update attempt by uid {}: {}", uid, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Creates a photo update request for the authenticated student.
     */
    @PostMapping("/request-photo-update")
    @RequireRole("student")
    public ResponseEntity<Map<String, Object>> requestPhotoUpdate(Authentication authentication) {
        String uid = (String) authentication.getPrincipal();
        log.info("Photo update requested by uid: {}", uid);
        try {
            profileService.requestPhotoUpdate(uid);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Photo update request submitted for manual review.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating photo update request: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
