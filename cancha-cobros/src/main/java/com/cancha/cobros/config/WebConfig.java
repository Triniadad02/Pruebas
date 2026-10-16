package com.cancha.cobros.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Qué rutas protege el PIN:
 *   protegidas:  /  (panel), /partidos (crear), /jugadores/**
 *   libres:      /e/{token} (cobrador, protegido por su token secreto), /v/{token} (solo lectura)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.admin-pin:}")
    private String adminPin;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminInterceptor(adminPin))
                .addPathPatterns("/", "/partidos", "/jugadores", "/jugadores/**");
    }
}
