package com.sarim.digitalbanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("banking")
                    .withUsername("banking")
                    .withPassword("banking");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.jwt.secret", () -> "test-secret-should-be-long-enough-for-jwt-signing-123456");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        try (var connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    protected RegisteredUser registerUser(String email, String password) throws Exception {
        JsonNode body = postJson(
                "/api/auth/register",
                null,
                "register-" + UUID.randomUUID(),
                Map.of(
                        "email", email,
                        "password", password
                ),
                status().isCreated()
        );

        return new RegisteredUser(
                body.get("userId").asLong(),
                body.get("accountId").asLong(),
                email,
                password
        );
    }

    protected String login(String email, String password) throws Exception {
        JsonNode body = postJson(
                "/api/auth/login",
                null,
                "login-" + UUID.randomUUID(),
                Map.of(
                        "email", email,
                        "password", password
                ),
                status().isOk()
        );

        return "Bearer " + body.get("accessToken").asText();
    }

    protected AdminUser createAdminAndLogin() throws Exception {
        String email = uniqueEmail("admin");
        String password = "Password123!";

        UserEntity admin = new UserEntity();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(admin);

        String bearerToken = login(email, password);
        return new AdminUser(email, password, bearerToken);
    }

    protected long createPayee(String bearerToken, String payeeEmail, String label) throws Exception {
        JsonNode body = postJson(
                "/api/payees",
                bearerToken,
                "payee-" + UUID.randomUUID(),
                Map.of(
                        "email", payeeEmail,
                        "label", label
                ),
                status().isOk()
        );

        return body.get("id").asLong();
    }

    protected JsonNode adminDeposit(String adminBearerToken, long toAccountId, long amountCents) throws Exception {
        return postJson(
                "/api/admin/deposit",
                adminBearerToken,
                "deposit-" + UUID.randomUUID(),
                Map.of(
                        "toAccountId", toAccountId,
                        "amountCents", amountCents
                ),
                status().isOk()
        );
    }

    protected JsonNode postJson(
            String url,
            String bearerToken,
            String idempotencyKey,
            Object payload,
            ResultMatcher expectedStatus
    ) throws Exception {
        var request = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(objectMapper.writeValueAsString(payload));

        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, bearerToken);
        }

        var response = mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn()
                .getResponse();

        String content = response.getContentAsString();
        if (content == null || content.isBlank()) {
            return objectMapper.createObjectNode();
        }

        return objectMapper.readTree(content);
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@test.local";
    }

    protected record RegisteredUser(Long userId, Long accountId, String email, String password) {}
    protected record AdminUser(String email, String password, String bearerToken) {}
}