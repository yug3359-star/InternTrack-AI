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
    @Email(message = "Invalid email format")
    private String collegeEmail;

    @NotBlank(message = "Account security password required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Academic engineering branch required")
    private String branch;

    @NotBlank(message = "College Roll No. required")
    private String rollNo;

    @NotBlank(message = "Registration No. required")
    private String registrationNumber;

    @NotBlank(message = "Section required")
    @Pattern(regexp = "^[A-C]$", message = "Section must be A, B, or C")
    private String section;

    @NotNull(message = "Semester required")
    @Min(value = 1, message = "Semester must be between 1 and 8")
    @Max(value = 8, message = "Semester must be between 1 and 8")
    private Integer semester;

    @NotBlank(message = "Mobile number required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number")
    private String mobileNumber;

    // Step 2: Internship Schedule
    @NotBlank(message = "Assigned faculty mentor name required")
    private String mentorName;

    @NotBlank(message = "Faculty mentor email required")
    @Email(message = "Valid RFC mentor email required")
    private String mentorEmail;

    @NotBlank(message = "Internship domain required")
    private String internshipDomain;

    @NotBlank(message = "Company name required")
    private String companyName;

    @NotBlank(message = "Mode of internship required (e.g. On-site, Remote, Hybrid)")
    private String modeOfInternship;

    @NotBlank(message = "Company Address/City required")
    private String companyAddress;

    @NotBlank(message = "Internship stipend required (e.g. 10000 INR, Unpaid)")
    private String internshipStipend;

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

