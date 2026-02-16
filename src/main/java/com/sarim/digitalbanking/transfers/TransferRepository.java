package com.sarim.digitalbanking.transfers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);

    // scoped replay: only allow returning an existing transfer if it belongs to the actor
    Optional<TransferEntity> findByIdempotencyKeyAndFromAccount_User_Id(String idempotencyKey, Long actorUserId);

    // used to detect "key exists but not for you" and return 409 cleanly
    boolean existsByIdempotencyKey(String idempotencyKey);
}
