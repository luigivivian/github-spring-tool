package dev.luigivivian.githubtool.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Vite dev server fallback; primary path is the /api proxy (same origin)
        registry.addMapping("/api/**").allowedOrigins("http://localhost:5173");
    }
}
