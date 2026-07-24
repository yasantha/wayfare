package com.wayfare.auth.spike;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * THROWAWAY Phase 0 spike (GETTING-STARTED Day 5). One endpoint that:
 *   1. writes a row to Postgres via a Flyway-created table,
 *   2. publishes a message to Kafka,
 *   3. returns 200.
 *
 * <p>Purpose: prove every moving part talks to every other one BEFORE real code
 * depends on it. After you confirm the row in Postgres, the message in Redpanda
 * Console (http://localhost:8090), and the trace in Jaeger (http://localhost:16686),
 * delete this whole package + V1__spike_ping.sql and reset the DB volume.
 *
 * <p>Try it (auth-service running, stack up):
 * <pre>curl -X POST "http://localhost:8081/spike?note=hello"</pre>
 */
@RestController
public class SpikeController {

    private static final Logger log = LoggerFactory.getLogger(SpikeController.class);
    private static final String TOPIC = "spike.ping";

    private final SpikePingRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SpikeController(SpikePingRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/spike")
    public Map<String, Object> ping(@RequestParam(defaultValue = "hello") String note) {
        // 1. Postgres write (table created by Flyway V1)
        SpikePing saved = repository.save(new SpikePing(note));
        log.info("spike: wrote row id={}", saved.getId());

        // 2. Kafka publish — the trace continues across the producer send
        kafkaTemplate.send(TOPIC, saved.getId().toString(), note);
        log.info("spike: published to topic={}", TOPIC);

        // 3. 200 OK
        return Map.of(
                "id", saved.getId(),
                "note", note,
                "topic", TOPIC,
                "message", "wrote to Postgres and published to Kafka"
        );
    }
}
