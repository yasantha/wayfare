package com.wayfare.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "prompt_templates")
public class PromptTemplate {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    @Column(name = "user_template", nullable = false)
    private String userTemplate;

    @Column(nullable = false)
    private boolean active = true;

    protected PromptTemplate() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public boolean isActive() {
        return active;
    }
}
