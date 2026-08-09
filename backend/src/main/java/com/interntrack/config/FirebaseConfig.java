package com.interntrack.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

/**
 * Configures the Firebase Admin SDK client for institutional authentication and database management.
 * Exposes Spring @Bean instances for FirebaseApp, FirebaseAuth, and Firestore for clean dependency injection.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${interntrack.firebase.config-path:${FIREBASE_CREDENTIALS_PATH:classpath:firebase-service-account.json}}")
    private String firebaseConfigPath;

    @Value("${interntrack.firebase.storage-bucket:${FIREBASE_STORAGE_BUCKET:interntrack-ai.appspot.com}}")
    private String firebaseStorageBucket;

    @Value("${interntrack.firebase.enabled:true}")
    private boolean firebaseEnabled;

    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public FirebaseApp firebaseApp() {
        if (!firebaseEnabled) {
            log.warn("Firebase integration is explicitly disabled via properties.");
            return null;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try {
            Resource resource = resourceLoader.getResource(firebaseConfigPath);
            if (!resource.exists()) {
                log.warn("=========================================================================================");
                log.warn("[WARNING] Firebase service account file not found at: {}", firebaseConfigPath);
                log.warn("To enable real Firebase ID token verification, place your credentials at ");
                log.warn("'backend/src/main/resources/firebase-service-account.json' or set FIREBASE_CREDENTIALS_PATH.");
                log.warn("Running in DEV MODE without active FirebaseAdmin cloud runtime connection.");
                log.warn("=========================================================================================");
                return null;
            }

            try (InputStream serviceAccount = resource.getInputStream()) {
                FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount));

                if (firebaseStorageBucket != null && !firebaseStorageBucket.trim().isEmpty()) {
                    optionsBuilder.setStorageBucket(firebaseStorageBucket.trim());
                }

                FirebaseOptions options = optionsBuilder.build();
                log.info("Successfully loaded Firebase Admin SDK credentials from [{}] for bucket [{}]", firebaseConfigPath, firebaseStorageBucket);
                return FirebaseApp.initializeApp(options);
            }
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseAdmin runtime: {}. Operating in offline dev mode.", e.getMessage());
            return null;
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth(@Autowired(required = false) FirebaseApp firebaseApp) {
        if (firebaseApp != null) {
            log.info("Initialized FirebaseAuth Spring @Bean successfully.");
            return FirebaseAuth.getInstance(firebaseApp);
        }
        log.warn("FirebaseAuth Bean initialized as empty due to missing FirebaseApp connection.");
        return null;
    }

    @Bean
    public Firestore firestore(@Autowired(required = false) FirebaseApp firebaseApp) {
        if (firebaseApp != null) {
            log.info("Initialized Firestore Spring @Bean successfully.");
            return FirestoreClient.getFirestore(firebaseApp);
        }
        log.warn("Firestore Bean initialized as empty due to missing FirebaseApp connection.");
        return null;
    }
}
