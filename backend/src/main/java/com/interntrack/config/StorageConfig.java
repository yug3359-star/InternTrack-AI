package com.interntrack.config;

import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Firebase Storage client access, exposing the cloud Bucket as a Spring @Bean
 * for document upload, portrait storage, and compliance optical check-ins.
 * Bucket name is dynamically injected from environment variables (FIREBASE_STORAGE_BUCKET).
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Value("${interntrack.firebase.storage-bucket:${FIREBASE_STORAGE_BUCKET:interntrack-ai.appspot.com}}")
    private String configuredBucketName;

    @Bean
    public Bucket firebaseStorageBucket(@Autowired(required = false) FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            log.warn("Firebase Storage Bucket @Bean not created: FirebaseApp connection is offline or in dev fallback.");
            return null;
        }

        try {
            StorageClient storageClient = StorageClient.getInstance(firebaseApp);
            Bucket bucket;
            
            if (configuredBucketName != null && !configuredBucketName.trim().isEmpty()) {
                bucket = storageClient.bucket(configuredBucketName.trim());
                log.info("Successfully bound Firebase Storage Bucket @Bean to target bucket: [{}]", configuredBucketName);
            } else {
                bucket = storageClient.bucket();
                log.info("Successfully bound Firebase Storage Bucket @Bean to default app options bucket.");
            }
            return bucket;
        } catch (Exception e) {
            log.error("Error initializing Firebase Storage Bucket @Bean for [{}] - {}. Relying on local simulation fallback.", configuredBucketName, e.getMessage());
            return null;
        }
    }
}
