package com.wayfare.auth.spike;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** THROWAWAY Phase 0 spike repository. Delete with the rest of this package. */
public interface SpikePingRepository extends JpaRepository<SpikePing, UUID> {
}
