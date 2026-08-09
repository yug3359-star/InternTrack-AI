package com.interntrack.service;

import com.interntrack.model.InternshipLog;
import com.interntrack.repository.InternshipLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating academic compliance checks, hours calculation,
 * and mentor verification actions for student internships.
 */
@Service
public class ComplianceMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceMonitoringService.class);
    private static final int REQUIRED_SEMESTER_HOURS = 160;

    private final InternshipLogRepository repository;

    public ComplianceMonitoringService(InternshipLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentComplianceSummary(String studentId) {
        List<InternshipLog> logs = repository.findByStudentIdOrderByWeekStartDateDesc(studentId);
        int totalHours = logs.stream()
                .filter(l -> "APPROVED".equalsIgnoreCase(l.getVerificationStatus()))
                .mapToInt(InternshipLog::getHoursLogged)
                .sum();

        double completionRate = Math.min(100.0, ((double) totalHours / REQUIRED_SEMESTER_HOURS) * 100.0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("studentId", studentId);
        summary.put("approvedHours", totalHours);
        summary.put("requiredHours", REQUIRED_SEMESTER_HOURS);
        summary.put("completionPercentage", Math.round(completionRate * 10.0) / 10.0);
        summary.put("complianceStatus", totalHours >= REQUIRED_SEMESTER_HOURS ? "SATISFIED" : "IN_PROGRESS");
        summary.put("totalSubmissions", logs.size());
        summary.put("recentLogs", logs.stream().limit(5).toList());

        return summary;
    }

    @Transactional
    public InternshipLog submitLog(InternshipLog logEntry) {
        if (logEntry.getWeekStartDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Submission rejected: week starting date cannot reside in future chronology.");
        }
        log.info("Recording weekly activity submission for student [{}], corporate entity [{}]", 
                logEntry.getStudentId(), logEntry.getCompanyName());
        return repository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentComplianceStats() {
        long totalPending = repository.countByVerificationStatus("PENDING");
        long totalApproved = repository.countByVerificationStatus("APPROVED");
        long totalRejected = repository.countByVerificationStatus("REJECTED");

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPendingReview", totalPending);
        stats.put("totalVerifiedApproved", totalApproved);
        stats.put("totalRejectedSubmissions", totalRejected);
        stats.put("departmentOverallComplianceRate", 78.5); // Baseline aggregate percentage for review demo
        return stats;
    }
}
