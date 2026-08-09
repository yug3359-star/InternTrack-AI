package com.interntrack.controller;

import com.interntrack.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * InternTrack AI — Institutional Analytics REST Controller (Module 10)
 * Exposes server-side aggregated academic KPIs, attendance series, diary compliance ledgers,
 * and departmental branch comparisons directly formatted for Recharts frontend visualization.
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired
    private AnalyticsService analyticsService;

    /**
     * GET /api/analytics/student/{uid}
     * Retrieves real-time candidate attendance rates, diary compliance, and exam score series.
     */
    @GetMapping("/student/{uid}")
    public ResponseEntity<Map<String, Object>> getStudentAnalytics(@PathVariable String uid) {
        logger.info("REST request to query Module 10 analytics series for candidate: [{}]", uid);
        Map<String, Object> data = analyticsService.getStudentAnalytics(uid);
        return ResponseEntity.ok(data);
    }

    /**
     * GET /api/analytics/hod/overview
     * Retrieves cached institution-wide population aggregates and escalation anomaly frequencies.
     */
    @GetMapping("/hod/overview")
    public ResponseEntity<Map<String, Object>> getHodOverview() {
        logger.info("REST request to query HOD institutional overview metrics (Module 10)");
        Map<String, Object> data = analyticsService.getHodOverview();
        return ResponseEntity.ok(data);
    }

    /**
     * GET /api/analytics/hod/branch-comparison
     * Retrieves average attendance and test evaluations grouped across engineering branch faculties.
     */
    @GetMapping("/hod/branch-comparison")
    public ResponseEntity<List<Map<String, Object>>> getHodBranchComparison() {
        logger.info("REST request to query departmental branch comparison ledgers (Module 10)");
        List<Map<String, Object>> data = analyticsService.getHodBranchComparison();
        return ResponseEntity.ok(data);
    }
}
