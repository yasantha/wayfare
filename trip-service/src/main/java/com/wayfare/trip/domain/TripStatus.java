package com.wayfare.trip.domain;

/** Design §4.4 saga: DRAFT -[generation]-> GENERATING -[succeeded]-> READY, or -[failed]-> DRAFT. */
public enum TripStatus {
    DRAFT,
    GENERATING,
    READY
}
