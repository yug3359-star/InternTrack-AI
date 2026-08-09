package com.interntrack.repository;

import com.interntrack.model.InternshipLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA database operations interface for student compliance logs.
 */
@Repository
public interface InternshipLogRepository extends JpaRepository<InternshipLog, Long> {

    List<InternshipLog> findByStudentIdOrderByWeekStartDateDesc(String studentId);
    
    List<InternshipLog> findByVerificationStatus(String status);
    
    long countByVerificationStatus(String status);
}
