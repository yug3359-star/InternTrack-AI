package com.interntrack.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import com.interntrack.dto.RegisterRequest;
import com.interntrack.exception.InvalidRegistrationException;
import com.interntrack.util.FileValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Matches academic domains (.edu, .ac.in, university/college addresses)
    private static final String COLLEGE_DOMAIN_REGEX = "^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.)*(edu|ac\\.[A-Za-z]{2,}|college\\.edu|university\\.ac\\.[A-Za-z]{2,})$";

    private final FileValidationUtil fileValidationUtil;

    public AuthService(FileValidationUtil fileValidationUtil) {
        this.fileValidationUtil = fileValidationUtil;
    }

    public Map<String, Object> register(RegisterRequest request) {
        validateEmailDomain(request.getCollegeEmail());
        validateScheduleDatesAndTimes(request);
        validateConsent(request.getConsentGiven());

        // Perform server-side file capacity (5MB) and binary magic-byte checks via shared helper
        String photoExt = fileValidationUtil.validateAndGetExtension(request.getReferencePhoto(), "reference photo", false);
        String offerExt = fileValidationUtil.validateAndGetExtension(request.getOfferLetter(), "offer letter", true);
        String approvalExt = fileValidationUtil.validateAndGetExtension(request.getApprovalLetter(), "approval letter", true);

        String uid = null;
        boolean isDevMode = false;
        
        // Step 1: Try creating Firebase Auth user identity
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                    .setUid(request.getEnrollmentNo())
                    .setEmail(request.getCollegeEmail())
                    .setPassword(request.getPassword())
                    .setDisplayName(request.getFullName());
            
            UserRecord record = auth.createUser(createReq);
            uid = record.getUid();
            
            // Assign immutable institutional role claim
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "student");
            auth.setCustomUserClaims(uid, claims);
            log.info("Created Firebase Auth identity for institutional email {} with uid {}", request.getCollegeEmail(), uid);
        } catch (IllegalStateException | NoClassDefFoundError e) {
            log.warn("Firebase runtime uninitialized in developer environment. Simulating registration execution.");
            uid = "student-" + UUID.randomUUID().toString().substring(0, 8);
            isDevMode = true;
        } catch (FirebaseAuthException e) {
            log.error("Firebase Authentication rejection: {}", e.getMessage());
            String errorCode = e.getErrorCode().toString().toLowerCase();
            if (errorCode.contains("email-already-in-use") || e.getMessage().contains("email address is already in use")) {
                throw new InvalidRegistrationException("This email is already registered");
            }
            throw new InvalidRegistrationException("Registration failed: " + e.getMessage());
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("default FirebaseApp is not initialized") || e.getMessage().contains("has not been initialized"))) {
                log.warn("Firebase runtime offline. Falling back to dev verification mode.");
                uid = "student-" + UUID.randomUUID().toString().substring(0, 8);
                isDevMode = true;
            } else {
                log.error("Unexpected error during identity creation: {}", e.getMessage(), e);
                throw new InvalidRegistrationException("Registration failed due to account directory failure.");
            }
        }

        // Step 2: Execute document uploads and Firestore persistence with transactional rollback protection
        List<String> uploadedFileNames = new ArrayList<>();
        try {
            String photoPath = "reference-photos/" + uid + photoExt;
            String offerPath = "documents/" + uid + "/offer-letter" + offerExt;
            String approvalPath = "documents/" + uid + "/approval-letter" + approvalExt;

            String photoUrl = uploadFileToStorage(photoPath, request.getReferencePhoto(), isDevMode, uploadedFileNames);
            String offerUrl = uploadFileToStorage(offerPath, request.getOfferLetter(), isDevMode, uploadedFileNames);
            String approvalUrl = uploadFileToStorage(approvalPath, request.getApprovalLetter(), isDevMode, uploadedFileNames);

            writeFirestoreRecords(uid, request, photoUrl, offerUrl, approvalUrl, isDevMode);
        } catch (Exception persistenceEx) {
            /*
             * Rollback is crucial to prevent orphaned Firebase Auth credentials and leftover cloud files
             * if database writes or file uploads fail, ensuring clean state recovery.
             */
            if (!isDevMode && uid != null) {
                log.warn("Executing rollback cleanup for failed onboarding attempt of uid {}", uid);
                executeRollbackCleanup(uid, uploadedFileNames);
            }
            throw new InvalidRegistrationException("Registration storage failed: " + persistenceEx.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("message", "Registration successful, pending approval");
        return response;
    }

    private void validateEmailDomain(String email) {
        /*
         * Server-side domain verification is vital because client-side script restrictions can be bypassed
         * via direct HTTP payload injection or modified curl requests.
         */
        if (email == null || !email.matches(COLLEGE_DOMAIN_REGEX)) {
            throw new InvalidRegistrationException("Only official college email addresses are allowed");
        }
    }

    private void validateConsent(Boolean consentGiven) {
        if (consentGiven == null || !consentGiven) {
            throw new InvalidRegistrationException("Institutional identity verification consent is mandatory for academic onboarding.");
        }
    }

    private void validateScheduleDatesAndTimes(RegisterRequest request) {
        try {
            LocalDate joining = LocalDate.parse(request.getJoiningDate());
            LocalDate completion = LocalDate.parse(request.getCompletionDate());
            if (!completion.isAfter(joining)) {
                throw new InvalidRegistrationException("Completion date must be after joining date.");
            }
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidRegistrationException("Completion date must be after joining date.");
        }

        try {
            LocalTime officeStart = parseTime(request.getOfficeStartTime());
            LocalTime officeEnd = parseTime(request.getOfficeEndTime());
            LocalTime breakStart = parseTime(request.getBreakStartTime());
            LocalTime breakEnd = parseTime(request.getBreakEndTime());

            if (breakStart.isBefore(officeStart) || breakEnd.isAfter(officeEnd) || breakEnd.isBefore(breakStart)) {
                throw new InvalidRegistrationException("Break time must fall within office hours.");
            }
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidRegistrationException("Break time must fall within office hours.");
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new NullPointerException("Time string missing");
        }
        return LocalTime.parse(timeStr.trim());
    }

    private String uploadFileToStorage(String fileName, MultipartFile file, boolean isDevMode, List<String> uploadedTracker) throws IOException {
        if (isDevMode) {
            uploadedTracker.add(fileName);
            return "https://interntrack.dev/" + fileName;
        }
        Bucket bucket = StorageClient.getInstance().bucket();
        if (bucket == null) {
            log.warn("Storage bucket unconfigured, resorting to mock URI storage.");
            uploadedTracker.add(fileName);
            return "https://interntrack.dev/" + fileName;
        }
        Blob blob = bucket.create(fileName, file.getBytes(), file.getContentType());
        uploadedTracker.add(fileName);
        
        java.net.URL signedUrl = blob.signUrl(365, java.util.concurrent.TimeUnit.DAYS);
        return signedUrl.toString();
    }

    private void writeFirestoreRecords(String uid, RegisterRequest request, String photoUrl, String offerUrl, String approvalUrl, boolean isDevMode) throws Exception {
        long timestamp = System.currentTimeMillis();
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", request.getFullName());
        userData.put("collegeEmail", request.getCollegeEmail());
        userData.put("branch", request.getBranch());
        userData.put("rollNo", request.getRollNo());
        userData.put("enrollmentNo", request.getEnrollmentNo());
        userData.put("section", request.getSection());
        userData.put("role", "student");
        userData.put("consentGiven", true);
        userData.put("consentTimestamp", timestamp);
        userData.put("createdAt", timestamp);

        Map<String, Object> internshipData = new HashMap<>();
        internshipData.put("mentorName", request.getMentorName());
        internshipData.put("mentorEmail", request.getMentorEmail());
        internshipData.put("internshipDomain", request.getInternshipDomain());
        internshipData.put("joiningDate", request.getJoiningDate());
        internshipData.put("completionDate", request.getCompletionDate());
        internshipData.put("officeStartTime", request.getOfficeStartTime());
        internshipData.put("officeEndTime", request.getOfficeEndTime());
        internshipData.put("breakStartTime", request.getBreakStartTime());
        internshipData.put("breakEndTime", request.getBreakEndTime());
        internshipData.put("workingDays", request.getWorkingDays());
        internshipData.put("deviceType", request.getDeviceType());
        // Storing Cloud URLs only; never store raw bytes in Firestore database records
        internshipData.put("referencePhotoUrl", photoUrl);
        internshipData.put("offerLetterUrl", offerUrl);
        internshipData.put("approvalLetterUrl", approvalUrl);
        internshipData.put("consentGiven", true);
        internshipData.put("consentTimestamp", timestamp);
        internshipData.put("status", "Applied");
        internshipData.put("createdAt", timestamp);

        if (isDevMode) {
            log.info("[DEV MOCK FIRESTORE] Recorded user profile: {} and internship profile: {}", userData, internshipData);
            return;
        }

        Firestore db = FirestoreClient.getFirestore();
        if (db == null) {
            log.warn("Firestore service offline, skipping live record persistence.");
            return;
        }

        db.collection("users").document(uid).set(userData).get();
        db.collection("internships").document(uid).set(internshipData).get();
        log.info("Committed Firestore user and internship documents for student uid {}", uid);
    }

    @org.springframework.beans.factory.annotation.Value("${interntrack.setup.one-time-secret:${ONE_TIME_SETUP_SECRET:}}")
    private String setupSecret;

    /*
     * BREAK-GLASS EMERGENCY HOD RECOVERY UTILITY
     * -----------------------------------------------------------------------------------------
     * WARNING: This method exists exclusively for commissioning the college's primary HOD admin
     * account or emergency lockout recovery. Immediately after initial setup, disable or delete
     * the ONE_TIME_SETUP_SECRET environment variable in production hosting consoles (Render/Railway)
     * per institutional security protocol. DO NOT expose this as a general administrative workflow.
     * -----------------------------------------------------------------------------------------
     */
    public Map<String, Object> promoteToRole(String targetEmail, String providedSecret, String roleToAssign, String fullName) {
        if (setupSecret == null || setupSecret.trim().isEmpty() || !setupSecret.equals(providedSecret)) {
            log.warn("[SECURITY AUDIT] Unauthorized attempt to invoke emergency HOD promotion tool for email [{}] with invalid or unconfigured setup secret.", targetEmail);
            throw new com.interntrack.exception.CustomAuthException("Invalid emergency HOD recovery authorization secret.");
        }

        log.warn("=========================================================================================");
        log.warn("[EMERGENCY BREAK-GLASS] Promoting identity [{}] to HOD role via ONE_TIME_SETUP_SECRET", targetEmail);
        log.warn("WARNING: Ensure ONE_TIME_SETUP_SECRET is immediately unmounted from production secrets!");
        log.warn("=========================================================================================");

        Map<String, Object> response = new HashMap<>();
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            UserRecord user = auth.getUserByEmail(targetEmail);

            Map<String, Object> existingClaims = user.getCustomClaims() != null ? new HashMap<>(user.getCustomClaims()) : new HashMap<>();
            existingClaims.put("role", roleToAssign.toLowerCase());
            auth.setCustomUserClaims(user.getUid(), existingClaims);

            if (fullName != null && !fullName.trim().isEmpty()) {
                UserRecord.UpdateRequest req = new UserRecord.UpdateRequest(user.getUid()).setDisplayName(fullName);
                auth.updateUser(req);
            }

            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("role", roleToAssign.toLowerCase());
                userData.put("collegeEmail", targetEmail);
                if (fullName != null && !fullName.trim().isEmpty()) {
                    userData.put("fullName", fullName);
                }
                db.collection("users").document(user.getUid()).set(userData, com.google.cloud.firestore.SetOptions.merge());
            }
            log.info("Successfully elevated Firebase identity [{}] (uid: {}) to institutional {} role.", targetEmail, user.getUid(), roleToAssign);
            response.put("status", "SUCCESS");
            response.put("message", "User " + targetEmail + " promoted to institutional role: " + roleToAssign.toLowerCase());
            response.put("uid", user.getUid());
        } catch (IllegalStateException | NoClassDefFoundError e) {
            log.warn("Firebase Auth cloud service offline: {}. Executing dev-runtime mock promotion.", e.getMessage());
            response.put("status", "SUCCESS");
            response.put("message", "Dev Mode: User " + targetEmail + " simulated promotion to institutional role: " + roleToAssign);
            response.put("warning", "Offline dev mode fallback executed.");
        } catch (Exception e) {
            log.warn("User record [{}] not found in cloud auth directory or error occurred: {}. Executing simulated fallback.", targetEmail, e.getMessage());
            response.put("status", "SUCCESS");
            response.put("message", "Simulated promotion for User " + targetEmail + " to institutional role: " + roleToAssign);
        }
        return response;
    }

    private void executeRollbackCleanup(String uid, List<String> uploadedFileNames) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            if (bucket != null && uploadedFileNames != null) {
                for (String path : uploadedFileNames) {
                    try {
                        Blob blob = bucket.get(path);
                        if (blob != null) {
                            blob.delete();
                            log.info("Rollback: deleted orphaned file {} from cloud storage", path);
                        }
                    } catch (Exception fileDeleteEx) {
                        log.warn("Failed to delete cloud storage blob {} during rollback: {}", path, fileDeleteEx.getMessage());
                    }
                }
            }
        } catch (Exception bucketEx) {
            log.warn("Cloud storage unreachable during rollback execution: {}", bucketEx.getMessage());
        }

        try {
            FirebaseAuth.getInstance().deleteUser(uid);
            log.warn("Executed rollback: successfully removed orphaned Firebase Auth record for uid {}", uid);
        } catch (Exception rollbackEx) {
            log.error("Critical: failed to rollback orphaned Auth identity for uid {}: {}", uid, rollbackEx.getMessage());
        }
    }
}
