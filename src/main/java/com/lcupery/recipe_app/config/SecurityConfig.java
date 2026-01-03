package com.lcupery.recipe_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()  // Allow CORS preflight requests
                        .requestMatchers(HttpMethod.GET, "/api/recipes/shared/**").permitAll()  // Allow public access to shared recipes
                        .requestMatchers("/api/recipes/**").authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}
