package io.grouploom.backend.membertoken;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.security.OpaqueTokens;

@Service
public class MemberTokenService {

	public record IssuedToken(String raw, Instant expiresAt) {
	}

	private final MemberTokenRepository repository;
	private final AppProperties properties;
	private final Clock clock;

	public MemberTokenService(MemberTokenRepository repository, AppProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	/** Creates a new token (earlier ones keep working) and returns the raw value; only its hash is stored. */
	@Transactional
	public IssuedToken issue(Member member, MemberTokenPurpose purpose) {
		String raw = OpaqueTokens.generate();
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.memberLinks().ttl());
		repository.save(new MemberToken(member, purpose, OpaqueTokens.hash(raw), expiresAt, now));
		return new IssuedToken(raw, expiresAt);
	}

	/** Returns the member a link belongs to, or throws {@link InvalidMemberTokenException}. */
	@Transactional
	public Member resolve(String raw, MemberTokenPurpose purpose) {
		if (raw == null || raw.isBlank()) {
			throw new InvalidMemberTokenException(InvalidMemberTokenException.INVALID);
		}
		MemberToken token = repository.findByTokenHash(OpaqueTokens.hash(raw))
			.filter(t -> t.getPurpose() == purpose && !t.isRevoked())
			.orElseThrow(() -> new InvalidMemberTokenException(InvalidMemberTokenException.INVALID));
		Instant now = clock.instant();
		if (!token.isUsable(now)) {
			throw new InvalidMemberTokenException(InvalidMemberTokenException.EXPIRED);
		}
		token.markUsed(now);
		return token.getMember();
	}

	@Transactional
	public void revokeAll(Member member) {
		repository.revokeAllForMember(member.getId(), clock.instant());
	}

}
