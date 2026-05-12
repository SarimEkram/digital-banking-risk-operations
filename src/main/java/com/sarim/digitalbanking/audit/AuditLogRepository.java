package com.sarim.digitalbanking.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    @Query(
            value = """
                    SELECT a FROM AuditLogEntity a
                    LEFT JOIN FETCH a.actorUser u
                    LEFT JOIN FETCH a.affectedUser af
                    WHERE (CAST(:action AS string) IS NULL OR LOWER(a.action) = LOWER(CAST(:action AS string)))
                      AND (:actorUserId IS NULL OR (u IS NOT NULL AND u.id = :actorUserId))
                      AND (
                            CAST(:actorRole AS string) IS NULL
                         OR (u IS NOT NULL AND UPPER(CAST(u.role AS string)) = UPPER(CAST(:actorRole AS string)))
                         OR (af IS NOT NULL AND UPPER(CAST(af.role AS string)) = UPPER(CAST(:actorRole AS string)))
                      )
                      AND (
                            CAST(:email AS string) IS NULL
                         OR (u IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
                         OR (af IS NOT NULL AND LOWER(af.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
                      )
                    ORDER BY a.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(a) FROM AuditLogEntity a
                    LEFT JOIN a.actorUser u
                    LEFT JOIN a.affectedUser af
                    WHERE (CAST(:action AS string) IS NULL OR LOWER(a.action) = LOWER(CAST(:action AS string)))
                      AND (:actorUserId IS NULL OR (u IS NOT NULL AND u.id = :actorUserId))
                      AND (
                            CAST(:actorRole AS string) IS NULL
                         OR (u IS NOT NULL AND UPPER(CAST(u.role AS string)) = UPPER(CAST(:actorRole AS string)))
                         OR (af IS NOT NULL AND UPPER(CAST(af.role AS string)) = UPPER(CAST(:actorRole AS string)))
                      )
                      AND (
                            CAST(:email AS string) IS NULL
                         OR (u IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
                         OR (af IS NOT NULL AND LOWER(af.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
                      )
                    """
    )
    Page<AuditLogEntity> search(
            @Param("action") String actionFilter,
            @Param("email") String emailFilter,
            @Param("actorRole") String actorRole,
            @Param("actorUserId") Long actorUserId,
            Pageable pageable
    );
}