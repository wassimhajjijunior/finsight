package com.finsight.budgetservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // The ID of the domain object this event is about
    // For transaction.created: the transaction ID
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    // The type of domain object
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    // The event type — consumers use this to decide how to handle it
    @Column(name = "event_type", nullable = false)
    private String eventType;

    // JSON payload — the full event data
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}