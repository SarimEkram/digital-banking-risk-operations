package com.sarim.digitalbanking.transfers;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Component
public class TransferPersistenceService {

    private final TransferRepository transferRepository;
    private final EntityManager entityManager;

    public TransferPersistenceService(
            TransferRepository transferRepository,
            EntityManager entityManager
    ) {
        this.transferRepository = transferRepository;
        this.entityManager = entityManager;
    }

    public SaveTransferOutcome saveTransferWithIdempotentReplay(
            TransferEntity candidate,
            String idempotencyKey,
            Supplier<Optional<TransferEntity>> replayLookup,
            Predicate<TransferEntity> sameRequest,
            boolean rethrowUnknownDuplicate
    ) {
        try {
            TransferEntity saved = transferRepository.saveAndFlush(candidate);
            entityManager.refresh(saved);
            return new SaveTransferOutcome(saved, false);
        } catch (DataIntegrityViolationException dup) {
            Optional<TransferEntity> winnerOpt = replayLookup.get();

            if (winnerOpt.isPresent()) {
                TransferEntity winner = winnerOpt.get();

                if (sameRequest.test(winner)) {
                    return new SaveTransferOutcome(winner, true);
                }

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"
                );
            }

            if (!rethrowUnknownDuplicate || transferRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used");
            }

            throw dup;
        }
    }

    public record SaveTransferOutcome(TransferEntity transfer, boolean replayed) {}
}