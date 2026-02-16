package com.sarim.digitalbanking.transfers;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);

    // scoped replay: only allow returning an existing transfer if it belongs to the actor
    Optional<TransferEntity> findByIdempotencyKeyAndFromAccount_User_Id(String idempotencyKey, Long actorUserId);

    // used to detect "key exists but not for you" and return 409 cleanly
    boolean existsByIdempotencyKey(String idempotencyKey);

    // FIRST PAGE (no cursor) — avoid null param typing issues in Postgres
    @Query("""
        select t
        from TransferEntity t
        where (t.fromAccount.user.id = :uid or t.toAccount.user.id = :uid)
        order by t.createdAt desc, t.id desc
    """)
    List<TransferEntity> findFirstPageForUser(
            @Param("uid") Long uid,
            Pageable pageable
    );

    // CURSOR PAGE — only called when cursor exists (params never null)
    @Query("""
        select t
        from TransferEntity t
        where (t.fromAccount.user.id = :uid or t.toAccount.user.id = :uid)
          and (t.createdAt < :beforeCreatedAt or (t.createdAt = :beforeCreatedAt and t.id < :beforeId))
        order by t.createdAt desc, t.id desc
    """)
    List<TransferEntity> findPageForUserBefore(
            @Param("uid") Long uid,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );
}
