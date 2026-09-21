package com.interntrack.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Map;

/**
 * Enterprise defensive Data Transfer Objects (DTOs) enforced across all Spring Boot REST controllers.
 * Replaces unvalidated Map payloads with strict declarative input sanitization using Spring Validation (@Valid).
 * Prevents NoSQL/SQL injection, Cross-Site Scripting (XSS) script sequences, buffer overflow floods,
 * and malicious payload manipulation before reaching institutional business logic.
 */
public class SecurityValidationDtos {

    public static class PromoteHodDto implements Serializable {
        @NotBlank(message = "Target institutional email cannot be blank")
        @Email(message = "Valid RFC academic email domain required to prevent credential spoofing")
        private String targetEmail;

        @NotBlank(message = "Setup secret token cannot be empty")
        @Size(min = 8, max = 128, message = "Setup secret length must be between 8 and 128 characters")
        private String setupSecret;

        private String fullName;

        public PromoteHodDto() {}
        public PromoteHodDto(String targetEmail, String setupSecret) {
            this.targetEmail = targetEmail;
            this.setupSecret = setupSecret;
        }

        public String getTargetEmail() { return targetEmail; }
        public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }
        public String getSetupSecret() { return setupSecret; }
        public void setSetupSecret(String setupSecret) { this.setupSecret = setupSecret; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }

    public static class DiarySubmitDto implements Serializable {
        @NotBlank(message = "Candidate profile UID required")
        @Size(max = 128, message = "UID cannot exceed 128 characters")
        private String uid;

        @NotBlank(message = "Chronological log date required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must adhere strictly to ISO-8601 YYYY-MM-DD format")
        private String date;

        @NotBlank(message = "Daily activity engineering narrative is mandatory")
        @Size(min = 5, max = 5000, message = "Diary narrative must be between 5 and 5000 characters to prevent buffer DoS floods")
        private String entryText;

        @Size(max = 200, message = "Candidate display name cannot exceed 200 characters")
        private String studentName;

        private Boolean rejectedDueToFaceMismatch;

        public DiarySubmitDto() {}

        public String getUid() { return uid; }
        public void setUid(String uid) { this.uid = uid; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getEntryText() { return entryText; }
        public void setEntryText(String entryText) { this.entryText = entryText; }
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public Boolean getRejectedDueToFaceMismatch() { return rejectedDueToFaceMismatch; }
        public void setRejectedDueToFaceMismatch(Boolean rejectedDueToFaceMismatch) { this.rejectedDueToFaceMismatch = rejectedDueToFaceMismatch; }
    }

    public static class ApproveApplicationDto implements Serializable {
        @NotBlank(message = "College Mentor allocation is required")
        private String collegeMentor;

        public ApproveApplicationDto() {}

        public String getCollegeMentor() { return collegeMentor; }
        public void setCollegeMentor(String collegeMentor) { this.collegeMentor = collegeMentor; }
    }

    public static class RejectReasonDto implements Serializable {
        @Size(max = 500, message = "Audit rejection explanation cannot exceed 500 characters")
        @Pattern(regexp = "^[^<>\"']*$", message = "Rejection reason contains illegal HTML tags or script sequence tokens (XSS mitigation)")
        private String reason;

        private String status;

        public RejectReasonDto() {}

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class AttendanceRespondDto implements Serializable {
        @NotBlank(message = "Attendance response action required")
        @Pattern(regexp = "^(present|meeting|in_meeting|absent)$", message = "Attendance action must strictly equal present, meeting, in_meeting, or absent")
        private String action;

        public AttendanceRespondDto() {}

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    public static class PopupRespondDto implements Serializable {
        @NotBlank(message = "Response status required")
        @Pattern(regexp = "^(present|in_meeting|meeting)$", message = "Popup status must strictly equal present or in_meeting")
        private String responseStatus;

        @Size(max = 1000, message = "Webcam URL parameter cannot exceed 1000 characters")
        private String photoUrl;

        @Size(max = 500, message = "Meeting justification cannot exceed 500 characters")
        private String meetingReason;

        public PopupRespondDto() {}

        public String getResponseStatus() { return responseStatus; }
        public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getMeetingReason() { return meetingReason; }
        public void setMeetingReason(String meetingReason) { this.meetingReason = meetingReason; }
    }

    public static class ApplicationCrudDto implements Serializable {
        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "College email is required")
        @Email(message = "Valid email is required")
        private String collegeEmail;

        @NotBlank(message = "Branch is required")
        private String branch;

        @NotBlank(message = "Internship domain is required")
        private String internshipDomain;

        private String mentorName;
        private String mentorEmail;

        @NotBlank(message = "Joining date is required")
        private String joiningDate;

        @NotBlank(message = "Completion date is required")
        private String completionDate;

        @NotBlank(message = "Status is required")
        private String status;

        public ApplicationCrudDto() {}

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getCollegeEmail() { return collegeEmail; }
        public void setCollegeEmail(String collegeEmail) { this.collegeEmail = collegeEmail; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getInternshipDomain() { return internshipDomain; }
        public void setInternshipDomain(String internshipDomain) { this.internshipDomain = internshipDomain; }
        public String getMentorName() { return mentorName; }
        public void setMentorName(String mentorName) { this.mentorName = mentorName; }
        public String getMentorEmail() { return mentorEmail; }
        public void setMentorEmail(String mentorEmail) { this.mentorEmail = mentorEmail; }
        public String getJoiningDate() { return joiningDate; }
        public void setJoiningDate(String joiningDate) { this.joiningDate = joiningDate; }
        public String getCompletionDate() { return completionDate; }
        public void setCompletionDate(String completionDate) { this.completionDate = completionDate; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
