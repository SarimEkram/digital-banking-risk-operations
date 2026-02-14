package com.sarim.digitalbanking.transfers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {
    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);
}
