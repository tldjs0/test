package com.gojeom.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Access 30분 / Refresh 14일. (API.md §2) */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, int accessMinutes, int refreshDays) {
}
