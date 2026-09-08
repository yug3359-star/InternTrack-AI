package com.interntrack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * InternTrack AI — Academic Internship Monitoring & Compliance Portal
 * Primary Boot Application bootstrap entry point.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
public class InternTrackApplication {

    private static final Logger log = LoggerFactory.getLogger(InternTrackApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InternTrackApplication.class, args);
        log.info("===================================================================");
        log.info("   INTERNTRACK AI SERVICE INITIALIZED - COLLEGE COMPLIANCE PORTAL  ");
        log.info("   Running on port 8080 | Stateless JWT Firebase Security Active   ");
        log.info("===================================================================");
    }
}
