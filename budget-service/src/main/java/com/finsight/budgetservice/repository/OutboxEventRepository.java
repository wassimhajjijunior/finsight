package com.finsight.budgetservice.repository;


import com.finsight.budgetservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    // Relay polls for these — ordered oldest first
    // Limit 100 per poll to avoid processing huge batches
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false " +
            "ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents(
            org.springframework.data.domain.Pageable pageable);

    // Efficient bulk mark-published after relay sends them
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.published = true, " +
            "o.publishedAt = :publishedAt WHERE o.id IN :ids")
    void markAsPublished(@Param("ids") List<UUID> ids, @Param("publishedAt") LocalDateTime publishedAt);
}