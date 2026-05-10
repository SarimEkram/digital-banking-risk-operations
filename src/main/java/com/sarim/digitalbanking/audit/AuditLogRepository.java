package com.sarim.digitalbanking.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /**
     * Paginated search over audit rows.
     *
     * All filters are optional and combine with AND:
     *  - actionFilter: exact match (case-insensitive) on the action column.
     *  - emailFilter:  case-insensitive substring match on the actor's email.
     *                  Rows with no actor are excluded whenever an email filter is supplied.
     *  - actorRole:    exact match (case-insensitive) on the actor's role (USER or ADMIN).
     *                  Rows with no actor are excluded whenever this filter is supplied,
     *                  since system rows have no role.
     *  - actorUserId:  exact match on the actor's user id. Used for the "self" scope so an
     *                  admin can default to seeing only their own audit entries.
     *
     * Ordered by id DESC so newest entries come first. id is a stable monotonic identity from the DB,
     * which avoids any dependency on created_at being populated on the in-flight entity.
     *
     * Notes on the query shape:
     *  - CAST(:param AS string) forces a portable string binding so Postgres doesn't fall back to
     *    bytea when the value is null (which would break LOWER() and string comparisons).
     *  - LEFT JOIN FETCH a.actorUser eagerly loads the user in the same SQL statement, so the
     *    controller can read getActorUser().getEmail() outside the JPA session without tripping
     *    LazyInitializationException, and avoids the N+1 query pattern.
     *  - countQuery is required when JOIN FETCH is used with Pageable: Spring Data needs a separate
     *    count query that does NOT include the fetch (counting joined rows would be wrong).
     */
    @Query(
            value = """
                    SELECT a FROM AuditLogEntity a
                    LEFT JOIN FETCH a.actorUser u
                    WHERE (CAST(:action AS string) IS NULL OR LOWER(a.action) = LOWER(CAST(:action AS string)))
                      AND (:actorUserId IS NULL OR (u IS NOT NULL AND u.id = :actorUserId))
                      AND (
                            CAST(:actorRole AS string) IS NULL
                         OR (u IS NOT NULL AND UPPER(CAST(u.role AS string)) = UPPER(CAST(:actorRole AS string)))
                      )
                      AND (
                            CAST(:email AS string) IS NULL
                         OR (u IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
                      )
                    ORDER BY a.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(a) FROM AuditLogEntity a
                    LEFT JOIN a.actorUser u
                    WHERE (CAST(:action AS string) IS NULL OR LOWER(a.action) = LOWER(CAST(:action AS string)))
                      AND (:actorUserId IS NULL OR (u IS NOT NULL AND u.id = :actorUserId))
                      AND (
                            CAST(:actorRole AS string) IS NULL
                         OR (u IS NOT NULL AND UPPER(CAST(u.role AS string)) = UPPER(CAST(:actorRole AS string)))
                      )
                      AND (
                            CAST(:email AS string) IS NULL
                         OR (u IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
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