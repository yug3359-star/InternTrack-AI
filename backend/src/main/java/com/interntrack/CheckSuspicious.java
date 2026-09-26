package com.interntrack;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CheckSuspicious implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        System.out.println("====== CHECKING SUSPICIOUS DIARIES ======");
        Firestore db = FirestoreClient.getFirestore();
        if (db != null) {
            List<QueryDocumentSnapshot> docs = db.collection("suspicious_diaries").get().get().getDocuments();
            System.out.println("Total suspicious diaries: " + docs.size());
            for (QueryDocumentSnapshot doc : docs) {
                System.out.println("Diary ID: " + doc.getId());
                System.out.println("UID: " + doc.getString("uid"));
                
                String uid = doc.getString("uid");
                if (uid != null) {
                    com.google.cloud.firestore.DocumentSnapshot internDoc = db.collection("internships").document(uid).get().get();
                    if (internDoc.exists()) {
                        System.out.println("  Student Internship Mentor: " + internDoc.getString("collegeMentor"));
                        System.out.println("  Student Assigned Mentor: " + internDoc.getString("assignedMentor"));
                    } else {
                        System.out.println("  Internship doc NOT FOUND for uid: " + uid);
                    }
                }
            }
        }
        System.out.println("=========================================");
    }
}
