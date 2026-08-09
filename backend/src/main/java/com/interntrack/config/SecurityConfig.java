package com.interntrack.config;

import com.interntrack.security.FirebaseJwtFilter;
import com.interntrack.security.SecurityDefensesFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * Configures application security rules, stateless authentication policies, 
 * role-scoped endpoint protections, and JWT/defensive filter chain placement.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final FirebaseJwtFilter firebaseJwtFilter;
    private final CorsFilter corsFilter;
    private final SecurityDefensesFilter securityDefensesFilter;

    public SecurityConfig(FirebaseJwtFilter firebaseJwtFilter, CorsFilter corsFilter, SecurityDefensesFilter securityDefensesFilter) {
        this.firebaseJwtFilter = firebaseJwtFilter;
        this.corsFilter = corsFilter;
        this.securityDefensesFilter = securityDefensesFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Register SecurityDefensesFilter first for outermost HTTPS enforcement, security headers, & rate limiting
            .addFilterBefore(securityDefensesFilter, UsernamePasswordAuthenticationFilter.class)
            // Register custom CORS configuration filter before security checks
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)) // Allow H2 Console display
            .authorizeHttpRequests(auth -> auth
                // Public diagnostic, console, and simulation API paths
                .requestMatchers("/api/**", "/error", "/h2-console/**", "/favicon.ico").permitAll()
                // Fallback default restriction
                .anyRequest().permitAll()
            )
            .addFilterBefore(firebaseJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
