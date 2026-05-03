package com.mockwise.interview.repository;

import com.mockwise.interview.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fed straight from the partial index {@code WHERE published = FALSE},
     * ordered so the poller drains the oldest first. Pageable lets the
     * poller cap each tick (don't try to publish 50k events in one batch).
     */
    @Query(
        "SELECT o FROM OutboxEvent o WHERE o.published = false ORDER BY o.createdAt ASC"
    )
    List<OutboxEvent> findUnpublished(Pageable pageable);

    @Modifying
    @Query(
        "UPDATE OutboxEvent o SET o.published = true, o.publishedAt = :publishedAt WHERE o.id = :id"
    )
    int markPublished(@Param("id") UUID id, @Param("publishedAt") OffsetDateTime publishedAt);
}
