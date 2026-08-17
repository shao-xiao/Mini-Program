package com.dehui.property.modules.shared.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DomainEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public DomainEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void audit(
            String aggregateType,
            Long aggregateId,
            String action,
            String beforeSnapshot,
            String afterSnapshot,
            Long operatorId,
            String requestId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO domain_audit_log (
                    aggregate_type, aggregate_id, action, before_snapshot, after_snapshot,
                    operator_id, request_id
                ) VALUES (?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                """,
                aggregateType,
                aggregateId,
                action,
                jsonOrEmpty(beforeSnapshot),
                jsonOrEmpty(afterSnapshot),
                operatorId,
                requestId
        );
    }

    public String outbox(
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payload
    ) {
        String eventId = UUID.randomUUID().toString();
        outbox(eventId, aggregateType, aggregateId, eventType, payload);
        return eventId;
    }

    public void outbox(
            String eventId,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payload
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_event (
                    event_id, aggregate_type, aggregate_id, event_type, payload, status
                ) VALUES (?, ?, ?, ?, CAST(? AS JSON), 'PENDING')
                """,
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                jsonOrEmpty(payload)
        );
    }

    private String jsonOrEmpty(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
