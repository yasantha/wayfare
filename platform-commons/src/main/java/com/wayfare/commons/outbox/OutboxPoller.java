package com.wayfare.commons.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes rows from this service's {@code outbox} table to Kafka and marks
 * them sent (design §3.3). Every service's outbox table shares the same shape
 * ({@code id, aggregate_type, aggregate_id, event_type, payload, correlation_id,
 * created_at, published_at, attempts}), so one implementation works everywhere
 * via plain JDBC rather than a per-service repository.
 *
 * <p>Each row is sent synchronously (bounded wait) before being marked
 * published, so a broker outage leaves the row retryable rather than lost —
 * this is what makes the outbox pattern safe: publish-then-mark, never the
 * reverse.
 */
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPoller(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                        int batchSize, long sendTimeoutMs) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${wayfare.outbox.poll-interval-ms:500}")
    public void poll() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, aggregate_id, event_type, payload, correlation_id from outbox " +
                "where published_at is null order by created_at limit ?", batchSize);

        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String aggregateId = String.valueOf(row.get("aggregate_id"));
            String eventType = String.valueOf(row.get("event_type"));
            String payload = String.valueOf(row.get("payload"));
            Object correlationId = row.get("correlation_id");

            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(eventType, aggregateId, payload);
                if (correlationId != null) {
                    record.headers().add("X-Correlation-Id",
                            String.valueOf(correlationId).getBytes(StandardCharsets.UTF_8));
                }
                kafka.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                jdbc.update("update outbox set published_at = now(), attempts = attempts + 1 where id = ?", id);
            } catch (Exception e) {
                jdbc.update("update outbox set attempts = attempts + 1 where id = ?", id);
                log.warn("Failed to publish outbox event {} (type={}), will retry next poll: {}",
                        id, eventType, e.getMessage());
            }
        }
    }
}
