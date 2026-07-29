package com.wayfare.ai.application;

import com.wayfare.ai.domain.GenerationRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Synchronous half of generation (design §7.2): check quota, dedupe by prompt
 * hash, persist {@code PENDING}, return {@code 202} immediately — the actual
 * work happens on {@link GenerationWorker}'s executor.
 *
 * <p>Deliberately NOT {@code @Transactional} itself: the transactional
 * persist step lives on {@link GenerationOutcomeService} (a separate bean),
 * and the async worker is only dispatched after that call returns — i.e.
 * after the row has committed. Dispatching from inside a transactional method
 * on {@code this} is a race: the async thread can start (virtual threads
 * start near-instantly) and query for the row before the transaction commits,
 * since {@code @Transactional} only commits when the proxied method returns.
 */
@Service
public class GenerationService {

    private final GenerationOutcomeService outcomeService;
    private final GenerationWorker worker;

    public GenerationService(GenerationOutcomeService outcomeService, GenerationWorker worker) {
        this.outcomeService = outcomeService;
        this.worker = worker;
    }

    public GenerationRequest requestGeneration(UUID tripId, UUID userId, UUID destinationId,
                                               LocalDate startDate, LocalDate endDate, int travelerCount,
                                               BigDecimal budgetAmount, String budgetCurrency,
                                               String correlationId) {
        String promptHash = hash(userId, destinationId, startDate, endDate, travelerCount,
                budgetAmount, budgetCurrency);

        GenerationOutcomeService.PendingResult result = outcomeService.createPending(tripId, userId, promptHash);

        if (result.isNew()) {
            var params = new GenerationWorker.GenerationParams(destinationId, startDate, endDate,
                    travelerCount, budgetAmount, budgetCurrency);
            worker.process(result.request().getId(), params, correlationId);
        }

        return result.request();
    }

    private static String hash(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            sb.append(p).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
