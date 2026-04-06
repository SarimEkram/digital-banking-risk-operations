package com.sarim.digitalbanking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarim.digitalbanking.transfers.api.TransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyReplayService {
    // TODO:
    // Current implementation stores completed responses in idempotency_keys and
    // relies on TransferPersistenceService for duplicate-save race safety.
    // Upgrade this to a stronger claim-state model (for example IN_PROGRESS +
    // COMPLETED) so retries are fully protected even if a request crashes after
    // the transfer row is saved but before the response is stored.
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyReplayService(
            IdempotencyKeyRepository idempotencyKeyRepository,
            ObjectMapper objectMapper
    ) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<TransferResponse> findStoredTransferReplay(String key, String requestHash) {
        return idempotencyKeyRepository.findByKey(key).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"
                );
            }

            if (record.getResponseCode() == null || record.getResponseBody() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key is already in use"
                );
            }

            return deserializeTransferResponse(record.getResponseBody());
        });
    }

    public void storeTransferResponse(String key, String requestHash, int responseCode, TransferResponse response) {
        IdempotencyKeyEntity record = idempotencyKeyRepository.findByKey(key)
                .orElseGet(() -> {
                    IdempotencyKeyEntity created = new IdempotencyKeyEntity();
                    created.setKey(key);
                    created.setRequestHash(requestHash);
                    created.setExpiresAt(Instant.now().plus(DEFAULT_TTL));
                    return created;
                });

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request"
            );
        }

        record.setResponseCode(responseCode);
        record.setResponseBody(serializeTransferResponse(response));

        if (record.getExpiresAt() == null) {
            record.setExpiresAt(Instant.now().plus(DEFAULT_TTL));
        }

        idempotencyKeyRepository.save(record);
    }

    private String serializeTransferResponse(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private TransferResponse deserializeTransferResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, TransferResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotent response", e);
        }
    }
}