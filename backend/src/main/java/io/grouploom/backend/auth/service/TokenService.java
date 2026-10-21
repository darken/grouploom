package io.grouploom.backend.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import io.grouploom.backend.config.JwtProperties;
import io.grouploom.backend.user.User;

@Service
public class TokenService {

	public record AccessToken(String value, long expiresIn) {
	}

	private final JwtEncoder encoder;
	private final JwtProperties properties;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AccessToken issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTtl()))
			.claim("gid", user.getGroup().getId().toString())
			.claim("role", user.getRole().name())
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new AccessToken(token, properties.accessTtl().toSeconds());
	}

}
