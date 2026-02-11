package com.sarim.digitalbanking.idempotency;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcIdempotencyConfig implements WebMvcConfigurer {

    private final IdempotencyKeyInterceptor interceptor;

    public WebMvcIdempotencyConfig(IdempotencyKeyInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns(
                        "/api/transfers/**",
                        "/api/admin/deposit"
                );
    }
}
