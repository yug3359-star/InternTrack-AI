package com.interntrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * InternTrack AI — Server-Side Institutional Query Caching (Module 10)
 * Provides an in-memory concurrent map cache space for high-cost HOD institutional
 * aggregate sweeps, enforced with an automated 5-minute eviction policy (TTL)
 * via Spring scheduling to maintain fresh analytics while protecting Firestore database limits.
 */
@Configuration
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);
    public static final String HOD_OVERVIEW_CACHE = "hodOverview";

    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOD_OVERVIEW_CACHE);

    @Bean
    public CacheManager cacheManager() {
        logger.info("Initializing InternTrack ConcurrentMapCacheManager for analytics optimization: [{}]", HOD_OVERVIEW_CACHE);
        return this.cacheManager;
    }

    /**
     * Automated Cache Eviction Worker
     * Executes every 300,000 milliseconds (5 minutes) to clear institutional summary caches.
     * Note: Only no-arg methods may be annotated with @Scheduled in Spring Boot.
     */
    @Scheduled(fixedRate = 300000)
    public void evictHodOverviewCache() {
        if (this.cacheManager.getCache(HOD_OVERVIEW_CACHE) != null) {
            this.cacheManager.getCache(HOD_OVERVIEW_CACHE).clear();
            logger.debug("[Module 10 Cache Audit] Evicted [{}] cache cleanly after 5-minute TTL cycle.", HOD_OVERVIEW_CACHE);
        }
    }
}
