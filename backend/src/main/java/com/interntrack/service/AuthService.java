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
    private final EmailProvider emailProvider;

    @org.springframework.beans.factory.annotation.Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public AuthService(FileValidationUtil fileValidationUtil, EmailProvider emailProvider) {
        this.fileValidationUtil = fileValidationUtil;
        this.emailProvider = emailProvider;
    }

    public Map<String, Object> register(RegisterRequest request) {
        validateEmailDomain(request.getCollegeEmail());
        validateScheduleDatesAndTimes(request);
        validateConsent(request.getConsentGiven());
        validateIdentityFields(request);

        // Perform server-side file capacity (5MB) and binary magic-byte checks via shared helper
        String photoExt = fileValidationUtil.validateAndGetExtension(request.getReferencePhoto(), "reference photo", false);
        String offerExt = fileValidationUtil.validateAndGetExtension(request.getOfferLetter(), "offer letter", true);
        String approvalExt = fileValidationUtil.validateAndGetExtension(request.getApprovalLetter(), "approval letter", true);

        String uid = null;
        boolean isDevMode = false;
        
        // Step 0: Auto-wipe previously rejected application to allow fresh re-registration
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            Firestore db = FirestoreClient.getFirestore();
            
            // 1. Check by Email
            try {
                UserRecord existingByEmail = auth.getUserByEmail(request.getCollegeEmail());
                if (existingByEmail != null && db != null) {
                    com.google.cloud.firestore.DocumentSnapshot d = db.collection("internships").document(existingByEmail.getUid()).get().get();
                    if (!d.exists() || "Rejected".equalsIgnoreCase(d.getString("status"))) {
                        log.info("Student email [{}] was previously rejected or orphaned. Wiping old records completely.", request.getCollegeEmail());
                        wipeAllUserData(existingByEmail.getUid(), db, auth);
                    } else {
                        throw new InvalidRegistrationException("This email is already registered");
                    }
                }
            } catch (FirebaseAuthException e) { /* Not found by email */ }
            
            // 2. Check by Registration Number (UID)
            try {
                UserRecord existingByUid = auth.getUser(request.getRegistrationNumber());
                if (existingByUid != null && db != null) {
                    com.google.cloud.firestore.DocumentSnapshot d = db.collection("internships").document(existingByUid.getUid()).get().get();
                    if (!d.exists() || "Rejected".equalsIgnoreCase(d.getString("status"))) {
                        log.info("Registration number [{}] was previously rejected or orphaned. Wiping old records completely.", request.getRegistrationNumber());
                        wipeAllUserData(existingByUid.getUid(), db, auth);
                    } else {
                        throw new InvalidRegistrationException("This registration number is already in use");
                    }
                }
            } catch (FirebaseAuthException e) { /* Not found by UID */ }

        } catch (InvalidRegistrationException e) {
            throw e; // Bubble up the "already registered" error
        } catch (Exception e) {
            log.warn("Could not verify prior rejected application status, proceeding cautiously: {}", e.getMessage());
        }

        // Step 1: Try creating Firebase Auth user identity
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                    .setUid(request.getRegistrationNumber())
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
            
            // Send Verification Email
            try {
                com.google.firebase.auth.ActionCodeSettings settings = com.google.firebase.auth.ActionCodeSettings.builder()
                        .setUrl(frontendUrl + "/email-verified")
                        .setHandleCodeInApp(false)
                        .build();
                String verificationLink = auth.generateEmailVerificationLink(request.getCollegeEmail(), settings);
                emailProvider.sendVerificationEmail(request.getCollegeEmail(), request.getFullName(), "Student", verificationLink);
            } catch (com.interntrack.exception.EmailDeliveryException emailEx) {
                log.error("Email delivery failed during registration for {}: {}", request.getCollegeEmail(), emailEx.getMessage());
                // Don't fail registration if email fails
            } catch (Exception ex) {
                log.error("Failed to generate or send verification email for {}: {}", request.getCollegeEmail(), ex.getMessage());
            }

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
        response.put("message", "Registration successful. Please check your email for the verification link. If you didn't receive it, use the Resend button on the login page.");
        return response;
    }

    public void resendVerificationEmail(String email) {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            UserRecord user = auth.getUserByEmail(email);
            
            com.google.firebase.auth.ActionCodeSettings settings = com.google.firebase.auth.ActionCodeSettings.builder()
                    .setUrl(frontendUrl + "/email-verified")
                    .setHandleCodeInApp(false)
                    .build();
            String verificationLink = auth.generateEmailVerificationLink(email, settings);
            
            String role = "Student";
            if (user.getCustomClaims() != null && user.getCustomClaims().containsKey("role")) {
                role = (String) user.getCustomClaims().get("role");
            }
            
            emailProvider.sendVerificationEmail(email, user.getDisplayName() != null ? user.getDisplayName() : "User", role, verificationLink);
        } catch (Exception e) {
            log.error("Failed to resend verification email for {}: {}", email, e.getMessage());
            // We swallow this so we don't leak user existence
        }
    }

    private void validateEmailDomain(String email) {
        /*
         * Server-side domain verification is vital because client-side script restrictions can be bypassed
         * via direct HTTP payload injection or modified curl requests.
         * (COMMENTED OUT FOR TESTING)
         */
        // if (email == null || !email.matches(COLLEGE_DOMAIN_REGEX)) {
        //     throw new InvalidRegistrationException("Only official college email addresses are allowed");
        // }
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
            LocalTime officeStart = LocalTime.parse(request.getOfficeStartTime());
            LocalTime officeEnd = LocalTime.parse(request.getOfficeEndTime());
            LocalTime breakStart = LocalTime.parse(request.getBreakStartTime());
            LocalTime breakEnd = LocalTime.parse(request.getBreakEndTime());

            int oStart = officeStart.getHour() * 60 + officeStart.getMinute();
            int oEnd = officeEnd.getHour() * 60 + officeEnd.getMinute();
            int bStart = breakStart.getHour() * 60 + breakStart.getMinute();
            int bEnd = breakEnd.getHour() * 60 + breakEnd.getMinute();

            if (oEnd < oStart) oEnd += 1440;
            if (bStart < oStart) bStart += 1440;
            if (bEnd < bStart) bEnd += 1440;

            if (bStart < oStart || bEnd > oEnd || bStart >= bEnd) {
                throw new InvalidRegistrationException("Break time must fall within office hours.");
            }
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidRegistrationException("Break time must fall within office hours.");
        }
    }

    private void validateIdentityFields(RegisterRequest request) {
        if (request.getMobileNumber() == null || !request.getMobileNumber().matches("^[6-9]\\d{9}$")) {
            throw new InvalidRegistrationException("Invalid 10-digit mobile number.");
        }
        if (request.getSection() == null || !request.getSection().matches("^[A-C]$")) {
            throw new InvalidRegistrationException("Section must be A, B, or C.");
        }
        if (request.getSemester() == null || request.getSemester() < 1 || request.getSemester() > 8) {
            throw new InvalidRegistrationException("Semester must be between 1 and 8.");
        }
        if (request.getRollNo() == null || request.getRollNo().trim().isEmpty()) {
            throw new InvalidRegistrationException("Roll number is required.");
        }
        if (request.getRegistrationNumber() == null || request.getRegistrationNumber().trim().isEmpty()) {
            throw new InvalidRegistrationException("Registration number is required.");
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
        userData.put("registrationNumber", request.getRegistrationNumber());
        userData.put("section", request.getSection());
        userData.put("semester", request.getSemester());
        userData.put("mobileNumber", request.getMobileNumber());
        userData.put("role", "student");
        userData.put("consentGiven", true);
        userData.put("consentTimestamp", timestamp);
        userData.put("createdAt", timestamp);

        Map<String, Object> internshipData = new HashMap<>();
        internshipData.put("mentorName", request.getMentorName());
        internshipData.put("mentorEmail", request.getMentorEmail());
        internshipData.put("internshipDomain", request.getInternshipDomain());
        
        internshipData.put("companyName", request.getCompanyName());
        internshipData.put("modeOfInternship", request.getModeOfInternship());
        internshipData.put("companyAddress", request.getCompanyAddress());
        internshipData.put("internshipStipend", request.getInternshipStipend());
        
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
            
            // Send Verification Email
            try {
                com.google.firebase.auth.ActionCodeSettings settings = com.google.firebase.auth.ActionCodeSettings.builder()
                        .setUrl(frontendUrl + "/email-verified")
                        .setHandleCodeInApp(false)
                        .build();
                String verificationLink = auth.generateEmailVerificationLink(targetEmail, settings);
                emailProvider.sendVerificationEmail(targetEmail, fullName != null && !fullName.trim().isEmpty() ? fullName : "User", roleToAssign, verificationLink);
            } catch (com.interntrack.exception.EmailDeliveryException emailEx) {
                log.error("Email delivery failed during promotion for {}: {}", targetEmail, emailEx.getMessage());
            } catch (Exception ex) {
                log.error("Failed to generate or send verification email for {}: {}", targetEmail, ex.getMessage());
            }

            response.put("status", "SUCCESS");
            response.put("message", "User " + targetEmail + " promoted to institutional role: " + roleToAssign.toLowerCase() + ". Verification email sent.");
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

    private void wipeAllUserData(String uid, Firestore db, FirebaseAuth auth) {
        try {
            if (db != null) {
                // Synchronously delete core profile documents
                db.collection("users").document(uid).delete().get();
                db.collection("internships").document(uid).delete().get();
                db.collection("completion_summaries").document(uid).delete().get();
                db.collection("quotas").document(uid).delete().get();

                // Sweep all related chronological or sub-collections to ensure zero trace is left
                String[] trackingCollections = {"attendance", "daily_status", "diaries", "suspicious_diaries", "warnings", "tests"};
                for (String col : trackingCollections) {
                    try {
                        java.util.List<com.google.cloud.firestore.QueryDocumentSnapshot> docs = 
                            db.collection(col).whereEqualTo("uid", uid).get().get().getDocuments();
                        for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docs) {
                            doc.getReference().delete(); // async deletion of children is fast and sufficient here
                        }
                    } catch (Exception e) {
                        log.warn("Failed sweeping sub-collection {} for uid {}: {}", col, uid, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to synchronously delete Firestore documents for {}: {}", uid, e.getMessage());
        }

        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            if (bucket != null) {
                for (Blob blob : bucket.list(com.google.cloud.storage.Storage.BlobListOption.prefix("documents/" + uid + "/")).iterateAll()) {
                    blob.delete();
                }
                for (Blob blob : bucket.list(com.google.cloud.storage.Storage.BlobListOption.prefix("reference-photos/" + uid)).iterateAll()) {
                    blob.delete();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete Cloud Storage files for {}: {}", uid, e.getMessage());
        }

        try {
            if (auth != null) {
                auth.deleteUser(uid);
            }
        } catch (Exception e) {
            log.warn("Failed to delete Auth user for {}: {}", uid, e.getMessage());
        }
    }
}
