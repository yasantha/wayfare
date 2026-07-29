package com.wayfare.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quota")
public record QuotaProperties(int perUserPerDay, int globalPerDay) {
}
