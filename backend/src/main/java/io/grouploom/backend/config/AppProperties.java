package io.grouploom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("grouploom")
public record AppProperties(
		/** Origin of the frontend, used to build links sent to members (no trailing slash needed). */
		@NotBlank String publicBaseUrl,
		@Valid @NotNull @DefaultValue MemberLinks memberLinks) {

	public record MemberLinks(@DefaultValue("60d") Duration ttl) {
	}

}
