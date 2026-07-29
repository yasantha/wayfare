package com.wayfare.ai.repository;

import com.wayfare.ai.domain.GenerationPayload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GenerationPayloadRepository extends JpaRepository<GenerationPayload, UUID> {
}
