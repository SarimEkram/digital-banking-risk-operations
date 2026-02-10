package com.sarim.digitalbanking.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IdentityController {

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication auth, HttpServletRequest request) {
        String email = auth.getName();

        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .map(a -> a.substring("ROLE_".length()))
                .orElse("UNKNOWN");

        Object uid = request.getAttribute("uid");

        return Map.of(
                "userId", uid,
                "email", email,
                "role", role
        );
    }
}
