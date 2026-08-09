package com.interntrack.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service providing customized examination questions for student biometrically proctored tests (Module 5c).
 * Directly queries real AI-generated exam question banks from Firestore test_questions collection,
 * only executing fallback to domain baseline items if no weekly exam bank exists yet.
 */
@Service
public class TestQuestionService {

    private static final Logger log = LoggerFactory.getLogger(TestQuestionService.class);

    @Autowired(required = false)
    private Firestore firestore;

    /**
     * Retrieves domain-tailored proctored exam questions for the student's active internship domain
     * by querying the real 'test_questions' collection produced by Module 6 weekly pipeline.
     */
    public List<Map<String, Object>> getQuestionsForStudent(String studentUid, String internshipDomain) {
        log.info("Querying customized examination question bank for student [{}] in domain [{}]", studentUid, internshipDomain);
        String domain = (internshipDomain != null && !internshipDomain.trim().isEmpty()) ? internshipDomain : "Software Architecture & Microservices";

        // 1. Prioritize real AI-generated exam banks from Firestore 'test_questions' collection
        if (firestore != null) {
            try {
                // Attempt direct document read first
                DocumentSnapshot doc = firestore.collection("test_questions").document(studentUid + "_WEEKLY_BANK").get().get();
                if (doc.exists() && doc.get("questions") != null) {
                    List<Map<String, Object>> realQuestions = (List<Map<String, Object>>) doc.get("questions");
                    if (!realQuestions.isEmpty()) {
                        log.info("[AI EXAM BANK FOUND] Successfully retrieved real AI-generated exam bank from Firestore test_questions for student [{}] (Source: {})", studentUid, doc.getString("source"));
                        return realQuestions;
                    }
                }

                // Attempt secondary query by student ID if document naming differs
                ApiFuture<QuerySnapshot> future = firestore.collection("test_questions").whereEqualTo("uid", studentUid).get();
                List<com.google.cloud.firestore.QueryDocumentSnapshot> docs = future.get().getDocuments();
                if (!docs.isEmpty()) {
                    List<Map<String, Object>> realQuestions = (List<Map<String, Object>>) docs.get(0).get("questions");
                    if (realQuestions != null && !realQuestions.isEmpty()) {
                        log.info("[AI EXAM BANK FOUND] Successfully retrieved queried AI question bank from Firestore test_questions for student [{}]", studentUid);
                        return realQuestions;
                    }
                }
            } catch (Exception e) {
                log.error("Exception querying Firestore test_questions collection for student [{}]: {}", studentUid, e.getMessage());
            }
        }

        // 2. Clear WARN-level fallback when genuinely no AI generated bank exists yet for that week
        log.warn("[AI EXAM BANK FALLBACK] No real AI-generated exam bank found in Firestore test_questions collection for student [{}]. Falling back to localized baseline examination questions for domain [{}].", studentUid, domain);
        return buildFallbackBaselineQuestions(domain);
    }

    private List<Map<String, Object>> buildFallbackBaselineQuestions(String domain) {
        List<Map<String, Object>> questions = new ArrayList<>();

        questions.add(buildQuestion(
            0,
            "Conceptual Evaluation (" + domain + "): Why is stateless authentication preferred in horizontal microservice scaling?",
            List.of(
                "It requires strict memory replication across all backend nodes.",
                "It enables load balancers to distribute requests arbitrarily without session affinity.",
                "It forces persistent WebSocket connections for all API requests.",
                "It eliminates the need for encryption across transport layers."
            ),
            1
        ));

        questions.add(buildQuestion(
            1,
            "Conceptual Evaluation (" + domain + "): What role does an idempotency key serve in enterprise payment and logging REST APIs?",
            List.of(
                "It compresses JSON payloads to save networking bandwidth.",
                "It prevents duplicate operations when network retries occur on timeouts.",
                "It acts as a primary firewall bypassing authentication filters.",
                "It automatically indexes database columns without explicit constraints."
            ),
            1
        ));

        questions.add(buildQuestion(
            2,
            "Practical Implementation (" + domain + "): While debugging an asynchronous worker queue experiencing thread exhaustion, which remediation approach is optimal?",
            List.of(
                "Increase thread pool limits infinitely until RAM is consumed.",
                "Implement bounded queues with exponential backoff and backpressure handling.",
                "Disable all exception logging inside thread workers.",
                "Convert all concurrent worker threads to synchronous blocking loops."
            ),
            1
        ));

        questions.add(buildQuestion(
            3,
            "Practical Implementation (" + domain + "): When querying high-volume Firestore ledgers, how do you prevent N+1 query overhead and memory exhaustion?",
            List.of(
                "Fetch all existing documents in the collection and filter array items in local memory.",
                "Utilize paginated query cursors with indexed compound filter constraints.",
                "Disable indexes on all collections to speed up write throughput.",
                "Store all records for all students in a single unbounded JSON blob document."
            ),
            1
        ));

        questions.add(buildQuestion(
            4,
            "Applied Production Engineering (" + domain + "): During a live deployment audit, an automated AWS Rekognition service returns unexpected rate limit exceptions. What is the standard engineering resolution?",
            List.of(
                "Delete all reference security portraits to bypass verification.",
                "Implement token bucket rate limiters combined with dead-letter queue (DLQ) re-evaluation.",
                "Restart the entire production database server.",
                "Switch all endpoints from HTTPS to unencrypted UDP."
            ),
            1
        ));

        return questions;
    }

    private Map<String, Object> buildQuestion(int id, String text, List<String> options, int correctIdx) {
        Map<String, Object> q = new HashMap<>();
        q.put("questionId", id);
        q.put("questionText", text);
        q.put("options", options);
        q.put("correctOptionIndex", correctIdx);
        return q;
    }
}
