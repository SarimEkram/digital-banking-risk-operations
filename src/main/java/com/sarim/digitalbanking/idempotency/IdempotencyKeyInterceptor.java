package com.sarim.digitalbanking.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Only enforce for write operations
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            return true;
        }

        // store normalized idempotency key for controllers/services to use later
        IdempotencyKeyUtil.requireAndStore(request);
        return true;
    }
}
