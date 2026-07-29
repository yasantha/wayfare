package com.wayfare.ai.repository;

import com.wayfare.ai.domain.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, UUID> {

    Optional<PromptTemplate> findFirstByNameAndActiveTrueOrderByVersionDesc(String name);
}
