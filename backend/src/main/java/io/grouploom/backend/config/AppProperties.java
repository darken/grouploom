package io.grouploom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties("grouploom")
public record AppProperties(
		/** Origin of the frontend, used to build links sent to members (no trailing slash needed). */
		@NotBlank String publicBaseUrl,
		@Valid @NotNull @DefaultValue MemberLinks memberLinks,
		@Valid @NotNull @DefaultValue Mail mail,
		@Valid @NotNull @DefaultValue Notifications notifications) {

	public record MemberLinks(@DefaultValue("60d") Duration ttl) {
	}

	public record Mail(
			/** Fixed sending address (SPF/DKIM); the group name is only the display name. */
			@NotBlank String from,
			@DefaultValue("true") boolean dispatcherEnabled,
			@DefaultValue("5s") Duration dispatchInterval,
			@DefaultValue("20") @Positive int batchSize,
			@DefaultValue("5") @Positive int maxAttempts,
			/** How long a dispatcher owns a claimed row before another may take it over. */
			@DefaultValue("2m") Duration claimTimeout,
			@DefaultValue("5m") Duration preferencesCooldown) {
	}

	public record Notifications(
			@DefaultValue("true") boolean fanOutEnabled,
			@DefaultValue("30s") Duration fanOutInterval,
			@DefaultValue("20") @Positive int batchSize,
			/** How long a fan-out owns a claimed notification before another may take it over. */
			@DefaultValue("2m") Duration claimTimeout) {
	}

}
