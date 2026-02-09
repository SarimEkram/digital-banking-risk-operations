package com.sarim.digitalbanking.auth;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    public record RegisterResult(Long userId, String email, Long accountId) {}
    public record LoginResult(Long userId, String email, String role, String accessToken, long expiresInSeconds) {}

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResult register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);

        UserEntity savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Email already registered");
        }

        AccountEntity account = new AccountEntity();
        account.setUser(savedUser);
        account.setCurrency("CAD");
        account.setBalanceCents(0L);
        account.setStatus("ACTIVE");

        AccountEntity savedAccount = accountRepository.save(account);

        return new RegisterResult(savedUser.getId(), savedUser.getEmail(), savedAccount.getId());
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);

        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.createAccessToken(user);

        return new LoginResult(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                token,
                jwtService.getExpirationSeconds()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("Email is required");
        return normalized;
    }
}
