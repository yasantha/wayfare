package com.wayfare.reco.application;

import com.wayfare.reco.repository.ScoringWeightRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/** Loads scoring_weights (design §8: tunable without redeployment). */
@Service
public class WeightsService {

    private final ScoringWeightRepository weights;

    public WeightsService(ScoringWeightRepository weights) {
        this.weights = weights;
    }

    public Map<String, BigDecimal> currentWeights() {
        return weights.findAll().stream()
                .collect(Collectors.toMap(w -> w.getKey(), w -> w.getValue()));
    }
}
