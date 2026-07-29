package com.wayfare.commons.autoconfigure;

import com.wayfare.commons.correlation.CorrelationIdFilter;
import com.wayfare.commons.error.GlobalExceptionHandler;
import com.wayfare.commons.outbox.OutboxPoller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Wires the cross-cutting beans so a service gets them just by depending on
 * platform-commons — no component scanning of the library required. All beans
 * are {@code @ConditionalOnMissingBean} so a service can override any of them.
 * Only active in a Servlet web application.
 *
 * <p>{@code @AutoConfigureAfter} is required here: {@code @ConditionalOnBean}
 * only sees beans registered before this class runs, so without an explicit
 * ordering hint the OutboxPoller bean could silently never be created if Boot
 * processes this configuration before its own Datasource/Jdbc/Kafka
 * auto-configurations.
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@AutoConfigureAfter({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        KafkaAutoConfiguration.class})
@EnableScheduling
public class WayfareCommonsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler wayfareGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Activates when a service has both a DataSource (via JdbcTemplate) and
     * Kafka (via KafkaTemplate) — i.e. it owns an outbox table. That bean-type
     * check alone isn't precise enough, though: Spring Boot auto-configures a
     * KafkaTemplate the moment spring-kafka is on the classpath, even for a
     * consumer-only service with no outbox table at all (recommendation-service:
     * consumes events, produces none, per the design's event catalogue). Such a
     * service must opt out explicitly via {@code wayfare.outbox.enabled=false},
     * or this poller queries a table that doesn't exist in its schema.
     */
    @Bean
    @ConditionalOnBean({JdbcTemplate.class, KafkaTemplate.class})
    @ConditionalOnMissingBean(OutboxPoller.class)
    @ConditionalOnProperty(prefix = "wayfare.outbox", name = "enabled", matchIfMissing = true)
    public OutboxPoller outboxPoller(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate,
                                     @Value("${wayfare.outbox.batch-size:100}") int batchSize,
                                     @Value("${wayfare.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        return new OutboxPoller(jdbcTemplate, kafkaTemplate, batchSize, sendTimeoutMs);
    }
}
