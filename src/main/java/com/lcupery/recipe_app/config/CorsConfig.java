package com.lcupery.recipe_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Temporarily allow all origins for debugging work laptop issue
        config.setAllowedOriginPatterns(List.of("*"));
        // config.setAllowedOrigins(List.of(
        //         "http://localhost:3000",
        //         "http://localhost:5173",
        //         "https://recipe-app-production-025b.up.railway.app",
        //         "https://recipe-app-ui-three.vercel.app",
        //         "https://planshopcook.app",
        //         "https://www.planshopcook.app"
        // ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);  // Apply to all paths
        return source;
    }
}