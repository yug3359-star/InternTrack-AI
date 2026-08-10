package com.interntrack.controller;

import com.interntrack.exception.InvalidRegistrationException;
import com.interntrack.service.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller governing Module 5c Personalized AI Proctored Tests, answer submission,
 * real-time server deadline enforcement, meeting quota reschedules, and faculty audit overrides.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @Autowired
    private TestService testService;

    @Autowired(required = false)
    private com.google.cloud.firestore.Firestore firestore;

    @Autowired
    private com.interntrack.service.AiPipelineService aiPipelineService;

    private final ZoneId zoneId = ZoneId.of("Asia/Kolkata");

    /**
     * POST /api/test/{testId}/start
     * Must be invoked within the 1-hour start window. Uploads webcam Start photo and validates FaceMatch similarity >40%.
     * Immutably initializes server-side testDeadline = actualStartTime + 20 minutes.
     */
    @PostMapping("/test/{testId}/start")
    public ResponseEntity<?> startTest(@PathVariable("testId") String testId, @RequestBody(required = false) Map<String, Object> payload) {
        log.info("Receiving proctored exam start request for test [{}]", testId);
        try {
            String photo = (payload != null && payload.get("startPhotoUrl") != null)
                ? (String) payload.get("startPhotoUrl")
                : "https://firebasestorage.googleapis.com/v0/b/interntrack-dev.appspot.com/o/test-captures%2Fstart_sim.jpg";

            Double similarityScore = (payload != null && payload.get("similarityScore") != null)
                ? ((Number) payload.get("similarityScore")).doubleValue()
                : 100.0;

            Map<String, Object> updated = testService.startTest(testId, photo, similarityScore);
            return ResponseEntity.ok(updated);
        } catch (InvalidRegistrationException e) {
            log.warn("Test start rejected for [{}]: {}", testId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", e.getMessage(),
                "testId", testId
            ));
        } catch (Exception e) {
            log.error("Internal exception starting test [{}]: {}", testId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/test/{testId}/submit-answer
     * Auto-saves an individual question answer during the exam. Rejects with 400 if server time exceeds testDeadline.
     */
    @PostMapping("/test/{testId}/submit-answer")
    public ResponseEntity<?> submitAnswer(@PathVariable("testId") String testId, @RequestBody Map<String, Object> payload) {
        try {
            if (payload == null || !payload.containsKey("questionIndex")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: questionIndex"));
            }
            int qIndex = ((Number) payload.get("questionIndex")).intValue();
            Object answer = payload.get("answer");

            Map<String, Object> res = testService.submitAnswer(testId, qIndex, answer);
            return ResponseEntity.ok(res);
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/test/{testId}/submit
     * Final submission of proctored exam with Submit webcam photo and tabSwitchCount.
     * Computes integer score out of 5 (0 answers = absent).
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/test/{testId}/submit")
    public ResponseEntity<?> submitTest(@PathVariable("testId") String testId, @RequestBody(required = false) Map<String, Object> payload) {
        log.info("Receiving final submission for test [{}]", testId);
        try {
            String submitPhoto = (payload != null && payload.get("submitPhotoUrl") != null)
                ? (String) payload.get("submitPhotoUrl")
                : "https://firebasestorage.googleapis.com/v0/b/interntrack-dev.appspot.com/o/test-captures%2Fsubmit_sim.jpg";

            int tabSwitches = (payload != null && payload.get("tabSwitchCount") != null)
                ? ((Number) payload.get("tabSwitchCount")).intValue()
                : 0;

            Map<String, Object> answers = (payload != null && payload.get("answers") instanceof Map)
                ? (Map<String, Object>) payload.get("answers")
                : null;

            Double similarityScore = (payload != null && payload.get("similarityScore") != null)
                ? ((Number) payload.get("similarityScore")).doubleValue()
                : 100.0;

            Map<String, Object> res = testService.submitTest(testId, submitPhoto, tabSwitches, answers, similarityScore);
            return ResponseEntity.ok(res);
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/test/{testId}/reschedule
     * Consumes 1 pass from shared monthly meeting quota. Only permitted exactly once per test document.
     */
    @PostMapping("/test/{testId}/reschedule")
    public ResponseEntity<?> rescheduleTest(@PathVariable("testId") String testId, @RequestBody(required = false) Map<String, Object> payload) {
        log.info("Requesting meeting quota reschedule for test [{}]", testId);
        try {
            String reason = (payload != null && payload.get("reason") != null) ? (String) payload.get("reason") : "meeting";
            Map<String, Object> res = testService.rescheduleTest(testId, reason);
            return ResponseEntity.ok(res);
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/test/history/{uid}
     * Returns student's historical proctored exam records.
     */
    @GetMapping("/test/history/{uid}")
    public ResponseEntity<Map<String, Object>> getTestHistory(@PathVariable("uid") String uid) {
        List<Map<String, Object>> list = testService.getStudentTestHistory(uid);
        Map<String, Object> resp = new HashMap<>();
        resp.put("uid", uid);
        resp.put("records", list);
        resp.put("count", list.size());
        return ResponseEntity.ok(resp);
    }

    /**
     * POST /api/test/trigger/{uid}
     * Manual demonstration evaluation trigger. Immediately creates a live test document in real Firestore.
     */
    @PostMapping("/test/trigger/{uid}")
    public ResponseEntity<Map<String, Object>> triggerTestNow(@PathVariable("uid") String uid, @RequestBody(required = false) Map<String, Object> payload) {
        log.info("Manually triggering live evaluation test for student [{}]", uid);
        
        try {
            log.info("Generating customized AI test questions for student [{}] before creating test document", uid);
            aiPipelineService.generateWeeklyTestQuestions(uid).get();
        } catch (Exception e) {
            log.error("Failed to generate AI questions prior to test creation: {}", e.getMessage());
        }
        
        String dateStr = LocalDate.now(zoneId).toString();
        String domain = (payload != null && payload.get("internshipDomain") != null) ? (String) payload.get("internshipDomain") : "Software Architecture & Microservices";
        
        String refUrl = null;
        try {
            if (firestore != null) {
                com.google.cloud.firestore.DocumentSnapshot doc = firestore.collection("internships").document(uid).get().get();
                if (doc.exists()) {
                    refUrl = doc.getString("referencePhotoUrl");
                }
            } else {
                log.warn("Firestore bean is null in TestController!");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch referencePhotoUrl from Firestore for uid: {}. Error: {}", uid, e.getMessage(), e);
        }
        
        if (refUrl == null) {
            refUrl = "https://firebasestorage.googleapis.com/v0/b/interntrack-ai-98f45.firebasestorage.app/o/reference-photos%2F" + uid + ".jpg";
        }


        Map<String, Object> newTest = testService.createTestDoc(uid, dateStr, System.currentTimeMillis(), 60, domain, refUrl);
        return ResponseEntity.ok(newTest);
    }

    /**
     * POST /api/mentor/test/{testId}/override-absent
     * Faculty Mentor RBAC endpoint: Manually overturns exam status to 'absent' with required remarks.
     */
    @PostMapping("/mentor/test/{testId}/override-absent")
    public ResponseEntity<?> overrideAbsent(@PathVariable("testId") String testId, @RequestBody Map<String, Object> payload) {
        try {
            String reason = (payload != null && payload.get("reason") != null) ? (String) payload.get("reason") : null;
            Map<String, Object> res = testService.overrideAbsent(testId, reason);
            return ResponseEntity.ok(res);
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
