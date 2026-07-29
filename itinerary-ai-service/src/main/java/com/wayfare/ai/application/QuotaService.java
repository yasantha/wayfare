package com.wayfare.ai.application;

import com.wayfare.ai.config.QuotaProperties;
import com.wayfare.ai.repository.GenerationRequestRepository;
import com.wayfare.commons.error.Exceptions.QuotaExceededException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Per-user daily generation cap, checked before any spend (design §7.6). */
@Service
public class QuotaService {

    private final GenerationRequestRepository requests;
    private final QuotaProperties props;

    public QuotaService(GenerationRequestRepository requests, QuotaProperties props) {
        this.requests = requests;
        this.props = props;
    }

    public void checkQuota(UUID userId) {
        Instant since = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long usedToday = requests.countByUserIdAndCreatedAtAfter(userId, since);
        if (usedToday >= props.perUserPerDay()) {
            throw new QuotaExceededException(
                    "Daily itinerary generation limit reached (%d/day)".formatted(props.perUserPerDay()));
        }
    }
}
