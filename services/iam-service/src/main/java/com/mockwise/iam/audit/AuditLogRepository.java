package com.mockwise.iam.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Filtered, paged query for the admin audit screen. Every filter is optional —
     * a {@code null} argument disables that clause. Caller supplies the sort
     * (default {@code occurred_at desc}) via {@link Pageable}.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorId    IS NULL OR a.actorId    = :actorId)
              AND (:action     IS NULL OR a.action     = :action)
              AND (:category   IS NULL OR a.category   = :category)
              AND (:targetType IS NULL OR a.targetType = :targetType)
              AND (:targetId   IS NULL OR a.targetId   = :targetId)
              AND (:outcome    IS NULL OR a.outcome    = :outcome)
              AND (:from       IS NULL OR a.occurredAt >= :from)
              AND (:to         IS NULL OR a.occurredAt <  :to)
            """)
    Page<AuditLog> search(
            @Param("actorId") String actorId,
            @Param("action") String action,
            @Param("category") String category,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("outcome") String outcome,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
