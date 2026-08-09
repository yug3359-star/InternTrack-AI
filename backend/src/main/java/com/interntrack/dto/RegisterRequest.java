package com.interntrack.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    // Step 1: Personal Info
    @NotBlank(message = "Full name cannot be empty")
    @Size(max = 100, message = "Full name exceeds maximum length of 100 characters")
    private String fullName;

    @NotBlank(message = "College academic email required")
    @Email(message = "Only official college email addresses are allowed (e.g. user@college.edu)")
    private String collegeEmail;

    @NotBlank(message = "Account security password required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Academic engineering branch required")
    private String branch;

    @NotBlank(message = "College Roll No. required")
    private String rollNo;

    @NotBlank(message = "Enrollment No. required")
    private String enrollmentNo;

    // Step 2: Internship Schedule
    @NotBlank(message = "Assigned faculty mentor name required")
    private String mentorName;

    @NotBlank(message = "Faculty mentor email required")
    @Email(message = "Valid RFC mentor email required")
    private String mentorEmail;

    @NotBlank(message = "Internship domain required")
    private String internshipDomain;

    @NotBlank(message = "Joining date is mandatory")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Joining date must match ISO YYYY-MM-DD pattern")
    private String joiningDate;

    @NotBlank(message = "Completion date is mandatory")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Completion date must match ISO YYYY-MM-DD pattern")
    private String completionDate;

    private String officeStartTime;
    private String officeEndTime;
    private String breakStartTime;
    private String breakEndTime;
    private List<String> workingDays;
    private String deviceType;

    // Step 3: Institutional Documents
    @NotNull(message = "Offer letter document upload is required")
    private MultipartFile offerLetter;

    @NotNull(message = "Institutional approval letter upload is required")
    private MultipartFile approvalLetter;

    // Step 4: Reference Photo & Verification Consent
    @NotNull(message = "Baseline reference portrait upload is required for facial authentication")
    private MultipartFile referencePhoto;

    @NotNull(message = "Institutional compliance and biometric verification consent is mandatory")
    private Boolean consentGiven;
}

