package io.grouploom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("grouploom.auth")
public record AuthProperties(
		@DefaultValue("14d") Duration refreshTtl,
		@DefaultValue("true") boolean cookieSecure) {
}
