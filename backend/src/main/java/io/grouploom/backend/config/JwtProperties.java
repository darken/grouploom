package io.grouploom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code secret} comes from GROUPLOOM_JWT_SECRET; blank means a random per-start key (dev/test only). */
@ConfigurationProperties("grouploom.jwt")
public record JwtProperties(
		@DefaultValue("") String secret,
		@DefaultValue("grouploom") String issuer,
		@DefaultValue("15m") Duration accessTtl) {
}
