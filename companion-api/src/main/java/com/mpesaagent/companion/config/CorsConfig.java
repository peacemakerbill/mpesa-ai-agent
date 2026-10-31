package com.mpesaagent.companion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Lets a web page on another address call this service from a browser, for example the test console in
 * console/console.html served by "python3 -m http.server". It is OFF unless cors.allowed-origins is set
 * (environment variable CORS_ALLOWED_ORIGINS, comma separated, for example http://localhost:8000).
 * <p>
 * Leave it empty in production. The internal endpoints use a shared key that must never be shipped to a browser.
 */
@Configuration
public class CorsConfig {

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(@Value("${cors.allowed-origins:}") String origins) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(corsFilter(origins));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Package visible so a test can build it without starting Spring. */
    static CorsFilter corsFilter(String origins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowed = Arrays.stream(origins == null ? new String[0] : origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty() && !s.equals("*")).toList();
        if (!allowed.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(allowed);
            config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept", "X-Internal-Key", "X-File-Name"));
            config.setExposedHeaders(List.of("Content-Disposition"));
            config.setMaxAge(600L);
            source.registerCorsConfiguration("/**", config);
        }
        return new CorsFilter(source);
    }
}
