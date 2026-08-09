package com.interntrack.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity representing a student's weekly internship compliance log entry.
 */
@Entity
@Table(name = "internship_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternshipLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Student roll number or college identifier required.")
    @Column(nullable = false, length = 64)
    private String studentId;

    @NotBlank(message = "Host corporate entity or research organization name required.")
    @Column(nullable = false, length = 128)
    private String companyName;

    @NotNull(message = "Week starting date required.")
    @Column(nullable = false)
    private LocalDate weekStartDate;

    @Min(value = 1, message = "Logged work duration must be at least 1 hour.")
    @Column(nullable = false)
    private Integer hoursLogged;

    @Column(length = 1000)
    private String taskSummary;

    @Column(nullable = false, length = 32)
    private String verificationStatus; // PENDING, APPROVED, REJECTED, FLAGGED

    @Column(length = 500)
    private String mentorRemarks;

    @Column(updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
        if (this.verificationStatus == null) {
            this.verificationStatus = "PENDING";
        }
    }
}
