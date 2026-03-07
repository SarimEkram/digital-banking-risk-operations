package com.sarim.digitalbanking.transfers;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<TransferEntity> findByIdempotencyKeyAndFromAccount_User_Id(String idempotencyKey, Long actorUserId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<TransferEntity> findByStatusOrderByCreatedAtAsc(TransferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from TransferEntity t
        where t.id = :id
    """)
    Optional<TransferEntity> findByIdForUpdate(@Param("id") Long id);

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