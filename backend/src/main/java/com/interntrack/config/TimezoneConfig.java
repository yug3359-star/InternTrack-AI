package com.interntrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Ensures deterministic server timezone behavior across all background jobs, cron tasks, and date comparisons.
 * Eliminates reliance on host operational system default timezones, preventing off-by-one-day timestamp flaws.
 */
@Configuration
public class TimezoneConfig {

    private static final Logger log = LoggerFactory.getLogger(TimezoneConfig.class);

    @Value("${interntrack.timezone:UTC}")
    private String configuredTimezone;

    @Bean
    public ZoneId applicationZoneId() {
        try {
            ZoneId zoneId = ZoneId.of(configuredTimezone);
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
            log.info("Institutional Server Timezone initialized to fixed target: [{}]", zoneId.getId());
            return zoneId;
        } catch (Exception e) {
            log.warn("Invalid timezone identifier [{}]. Defaulting to standard UTC: {}", configuredTimezone, e.getMessage());
            ZoneId fallbackZone = ZoneId.of("UTC");
            TimeZone.setDefault(TimeZone.getTimeZone(fallbackZone));
            return fallbackZone;
        }
    }
}
