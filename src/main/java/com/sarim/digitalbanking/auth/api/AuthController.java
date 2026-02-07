package com.sarim.digitalbanking.auth.api;

import com.sarim.digitalbanking.auth.AuthService;
import com.sarim.digitalbanking.auth.AuthService.RegisterResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        RegisterResult result = authService.register(req.email(), req.password());

        RegisterResponse body = new RegisterResponse(
                result.userId(),
                result.email(),
                result.accountId()
        );

        return ResponseEntity
                .created(URI.create("/api/users/" + result.userId()))
                .body(body);
    }
}
