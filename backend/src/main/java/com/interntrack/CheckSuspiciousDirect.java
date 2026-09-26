package com.interntrack;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.util.List;

public class CheckSuspiciousDirect {
    public static void main(String[] args) throws Exception {
        System.out.println("====== CHECKING SUSPICIOUS DIARIES ======");
        
        try {
            FileInputStream serviceAccount = new FileInputStream("src/main/resources/firebase-service-account.json");
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            Firestore db = FirestoreClient.getFirestore();
            List<QueryDocumentSnapshot> docs = db.collection("suspicious_diaries").get().get().getDocuments();
            System.out.println("Total suspicious diaries: " + docs.size());
            
            System.out.println("====== CHECKING MENTOR SUSPICIOUS LOGIC ======");
            String mentorName = "Nikita"; // Target mentor
            
            List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
            for (QueryDocumentSnapshot d : docs) {
                java.util.Map<String, Object> data = d.getData();
                String status = (String) data.getOrDefault("status", "rejected");
                
                System.out.println("Checking diary ID: " + d.getId() + ", Status: " + status);
                
                if ("rejected".equalsIgnoreCase(status) || "suspicious".equalsIgnoreCase(status)) {
                    String studentUid = (String) data.get("uid");
                    if (studentUid != null) {
                        com.google.cloud.firestore.DocumentSnapshot internDoc = db.collection("internships").document(studentUid).get().get();
                        if (internDoc.exists()) {
                            String cMentor = internDoc.getString("collegeMentor");
                            String aMentor = internDoc.getString("assignedMentor");
                            
                            String cleanCMentor = cMentor != null ? cMentor.trim() : "";
                            String cleanAMentor = aMentor != null ? aMentor.trim() : "";
                            String cleanMentorName = mentorName != null ? mentorName.trim() : "";
                            
                            System.out.println("  Student collegeMentor: '" + cleanCMentor + "'");
                            System.out.println("  Student assignedMentor: '" + cleanAMentor + "'");
                            System.out.println("  Mentor Name to Match: '" + cleanMentorName + "'");
                            
                            if (cleanMentorName.equalsIgnoreCase(cleanCMentor) || cleanMentorName.equalsIgnoreCase(cleanAMentor)) {
                                data.put("id", d.getId());
                                results.add(data);
                                System.out.println("  => ADDED to results!");
                            } else {
                                System.out.println("  => SKIPPED due to mismatch");
                            }
                        } else {
                            System.out.println("  Internship doc missing for UID: " + studentUid);
                        }
                    }
                }
            }
            
            System.out.println("Final Results Count: " + results.size());
            if (results.size() > 0) {
                System.out.println("Sorting results...");
                results.sort((a, b) -> {
                    Long tA = (Long) a.getOrDefault("flaggedAt", a.getOrDefault("submittedAt", 0L));
                    Long tB = (Long) b.getOrDefault("flaggedAt", b.getOrDefault("submittedAt", 0L));
                    return tB.compareTo(tA);
                });
                System.out.println("Sort completed successfully.");
                System.out.println("Sample Date: " + results.get(0).get("date"));
                System.out.println("Sample ID: " + results.get(0).get("id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=========================================");
        System.exit(0);
    }
}
