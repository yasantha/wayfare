package com.wayfare.trip.repository;

import com.wayfare.trip.domain.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Page<Trip> findByUserId(UUID userId, Pageable pageable);
}
