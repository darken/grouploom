package io.grouploom.backend.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.RefreshToken;
import io.grouploom.backend.auth.repository.RefreshTokenRepository;
import io.grouploom.backend.config.AuthProperties;
import io.grouploom.backend.error.InvalidRefreshTokenException;
import io.grouploom.backend.security.OpaqueTokens;
import io.grouploom.backend.user.User;

@Service
public class RefreshTokenService {

	public record Rotation(User user, String rawToken) {
	}

	private final RefreshTokenRepository repository;
	private final AuthProperties properties;
	private final Clock clock;

	public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	/** Creates a new refresh token and returns the raw value; only its hash is stored. */
	@Transactional
	public String issue(User user) {
		String raw = OpaqueTokens.generate();
		Instant now = clock.instant();
		repository.save(new RefreshToken(user, OpaqueTokens.hash(raw), now.plus(properties.refreshTtl()), now));
		return raw;
	}

	/**
	 * Revokes the presented token and issues a replacement. Presenting an already revoked token
	 * means it was stolen or replayed, so all of the user's tokens are revoked.
	 */
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public Rotation rotate(String rawToken) {
		Instant now = clock.instant();
		RefreshToken token = repository.findByTokenHash(OpaqueTokens.hash(rawToken))
			.orElseThrow(InvalidRefreshTokenException::new);
		if (token.isRevoked()) {
			repository.revokeAllForUser(token.getUser().getId(), now);
			throw new InvalidRefreshTokenException();
		}
		if (token.isExpired(now)) {
			throw new InvalidRefreshTokenException();
		}
		token.revoke(now);
		return new Rotation(token.getUser(), issue(token.getUser()));
	}

	@Transactional
	public void revoke(String rawToken) {
		repository.findByTokenHash(OpaqueTokens.hash(rawToken)).ifPresent(t -> {
			if (!t.isRevoked()) {
				t.revoke(clock.instant());
			}
		});
	}

}
