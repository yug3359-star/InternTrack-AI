package com.interntrack.scheduler;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.interntrack.service.AiPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Module 6: Scheduled Weekly AI Exam Question Generator.
 * Fires every Sunday at 02:00 AM UTC to read the accumulated 14-day work ledger of all active interns
 * and synthesize 5 customized proctored multiple-choice questions into Firestore test_questions collection.
 */
@Component
public class WeeklyQuestionGenJob {

    private static final Logger log = LoggerFactory.getLogger(WeeklyQuestionGenJob.class);

    @Autowired(required = false)
    private Firestore firestore;

    @Autowired
    private AiPipelineService aiPipelineService;

    @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
    public void generateWeeklyQuestionBanks() {
        log.info("[WEEKLY AI QUESTION GEN JOB] Triggering automated weekly exam synthesis for all active internships...");

        if (firestore != null) {
            try {
                ApiFuture<QuerySnapshot> future = firestore.collection("users").whereEqualTo("role", "student").get();
                List<com.google.cloud.firestore.QueryDocumentSnapshot> students = future.get().getDocuments();
                if (students.isEmpty()) {
                    log.info("[WEEKLY AI QUESTION GEN JOB] No active student accounts identified in Firestore. Executing demonstration run for sample profiles.");
                    executeDemoSynthesis();
                    return;
                }
                for (com.google.cloud.firestore.QueryDocumentSnapshot doc : students) {
                    String uid = doc.getId();
                    aiPipelineService.generateWeeklyTestQuestions(uid);
                }
            } catch (Exception e) {
                log.error("[WEEKLY AI QUESTION GEN JOB ERROR] Failed scanning users collection: {}. Running backup synthesis.", e.getMessage(), e);
                executeDemoSynthesis();
            }
        } else {
            log.info("[WEEKLY AI QUESTION GEN JOB STUB] Firestore inactive. Triggering weekly question generation for demo students.");
            executeDemoSynthesis();
        }
    }

    private void executeDemoSynthesis() {
        aiPipelineService.generateWeeklyTestQuestions("dev-stud-102");
        aiPipelineService.generateWeeklyTestQuestions("dev-student-01");
    }
}
