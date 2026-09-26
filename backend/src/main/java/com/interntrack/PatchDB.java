package com.interntrack;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

public class PatchDB {
    public static void main(String[] args) throws Exception {
        System.out.println("Initializing Firebase...");
        FileInputStream serviceAccount = new FileInputStream("src/main/resources/firebase-service-account.json");
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
        FirebaseApp.initializeApp(options);

        Firestore db = FirestoreClient.getFirestore();
        
        System.out.println("Patching attendance document for 2023_2026-09-24...");
        String docId = "2023_2026-09-24";
        Map<String, Object> update = new HashMap<>();
        update.put("status", "excused_meeting");
        update.put("absenceReason", "Claimed whole day meeting exemption token.");
        
        db.collection("attendance").document(docId).set(update, SetOptions.merge()).get();
        
        System.out.println("Patching daily_status document for 2023_2026-09-24...");
        Map<String, Object> update2 = new HashMap<>();
        update2.put("status", "excused_meeting");
        update2.put("attendanceStatus", "excused_meeting");
        db.collection("daily_status").document(docId).set(update2, SetOptions.merge()).get();
        
        System.out.println("Done! Patched successfully.");
        System.exit(0);
    }
}
