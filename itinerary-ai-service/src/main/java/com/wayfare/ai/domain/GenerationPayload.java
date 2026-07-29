package com.wayfare.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "generation_payloads")
public class GenerationPayload {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validated_payload", columnDefinition = "jsonb")
    private String validatedPayload;

    protected GenerationPayload() {
    }

    public static GenerationPayload of(UUID requestId, String rawResponse, String validatedPayload) {
        GenerationPayload p = new GenerationPayload();
        p.requestId = requestId;
        p.rawResponse = rawResponse;
        p.validatedPayload = validatedPayload;
        return p;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public String getValidatedPayload() {
        return validatedPayload;
    }
}
