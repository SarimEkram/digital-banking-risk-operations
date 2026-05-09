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
     * Both filters are optional:
     *  - actionFilter: when null/blank, no action filter is applied; otherwise exact match (case-insensitive).
     *  - emailFilter:  when null/blank, no email filter is applied; otherwise case-insensitive substring
     *                  match on the actor's email. Rows with no actor (system entries) are excluded
     *                  whenever an email filter is supplied, since they have no email to match.
     *
     * Ordered by id DESC so newest entries come first. id is a stable monotonic identity from the DB,
     * which avoids any dependency on created_at being populated on the in-flight entity.
     */
    @Query("""
            SELECT a FROM AuditLogEntity a
            LEFT JOIN a.actorUser u
            WHERE (:action IS NULL OR LOWER(a.action) = LOWER(:action))
              AND (
                    :email IS NULL
                 OR (u IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
              )
            ORDER BY a.id DESC
            """)
    Page<AuditLogEntity> search(
            @Param("action") String actionFilter,
            @Param("email") String emailFilter,
            Pageable pageable
    );
}