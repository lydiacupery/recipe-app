package com.lcupery.recipe_app.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "https://recipe-app-production-025b.up.railway.app",
            "https://recipe-app-ui-three.vercel.app",
            "https://planshopcook.app",
            "https://www.planshopcook.app",
            "https://planthenplate.com",
            "https://www.planthenplate.com"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");
        log.debug("CORS Filter - Origin: {}, Method: {}, Path: {}",
                origin, request.getMethod(), request.getRequestURI());

        // Check if origin is allowed
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, Accept, X-Requested-With, Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
            response.setHeader("Access-Control-Expose-Headers",
                    "Authorization, Content-Type, X-Requested-With");
            response.setHeader("Access-Control-Max-Age", "3600");

            log.debug("CORS headers added for origin: {}", origin);
        }

        // Handle preflight OPTIONS request
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.info("Handling OPTIONS preflight request for: {} from origin: {}",
                    request.getRequestURI(), origin);
            response.setStatus(HttpServletResponse.SC_OK);
            return; // Don't continue the filter chain for OPTIONS
        }

        chain.doFilter(req, res);
    }
}
