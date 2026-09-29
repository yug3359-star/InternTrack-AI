package com.interntrack.scheduler;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class BiometricRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(BiometricRetentionJob.class);

    @Autowired(required = false)
    private Firestore firestore;

    /**
     * Scheduled nightly cleanup job that runs daily at 02:30 AM.
     * Enforces the 7-day data retention policy on borderline face verification photos in Firebase.
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void performBiometricRetentionCleanup() {
        log.info("=================================================================================");
        log.info("[SCHEDULED RETENTION JOB] Starting automated 7-day pruning of face verification mentor reviews...");
        if (firestore == null) {
            log.warn("Firestore not configured, skipping biometric retention job.");
            return;
        }

        try {
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            
            Query query = firestore.collection("mentor_reviews")
                    .whereLessThanOrEqualTo("timestamp", sevenDaysAgo);
                    
            ApiFuture<QuerySnapshot> future = query.get();
            List<com.google.cloud.firestore.QueryDocumentSnapshot> docs = future.get().getDocuments();
            
            int deletedCount = 0;
            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docs) {
                String checkInPhotoUrl = doc.getString("checkInPhotoUrl");
                deleteFromStorageIfPossible(checkInPhotoUrl);
                
                doc.getReference().delete();
                deletedCount++;
            }
            log.info("[SCHEDULED RETENTION JOB] Successfully pruned {} biometric reviews and associated storage blobs.", deletedCount);
        } catch (Exception e) {
            log.error("[SCHEDULED RETENTION JOB] Error executing biometric pruning: {}", e.getMessage(), e);
        }
        log.info("=================================================================================");
    }

    private void deleteFromStorageIfPossible(String url) {
        if (url == null || !url.contains("firebasestorage.googleapis.com")) return;
        
        try {
            // Extract the path from the Firebase storage URL
            // e.g. https://firebasestorage.googleapis.com/v0/b/bucket-name.appspot.com/o/path%2Fto%2Ffile.jpg?alt=media...
            String[] parts = url.split("/o/");
            if (parts.length > 1) {
                String pathWithParams = parts[1];
                String encodedPath = pathWithParams.split("\\?")[0];
                String storagePath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8);
                
                Bucket bucket = StorageClient.getInstance().bucket();
                Blob blob = bucket.get(storagePath);
                if (blob != null) {
                    blob.delete();
                    log.info("Deleted expired biometric photo blob from cloud storage: {}", storagePath);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse or delete storage blob for URL [{}]: {}", url, e.getMessage());
        }
    }
}
