package io.grouploom.backend.membertoken;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.security.OpaqueTokens;

@Service
public class MemberTokenService {

	public record IssuedToken(String raw, Instant expiresAt) {
	}

	public record ResolvedAttendanceToken(Member member, Event event) {
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
		return issue(member, purpose, null);
	}

	/** Like {@link #issue(Member, MemberTokenPurpose)}, for a token bound to one event ({@code ATTENDANCE}). */
	@Transactional
	public IssuedToken issue(Member member, MemberTokenPurpose purpose, Event event) {
		String raw = OpaqueTokens.generate();
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.memberLinks().ttl());
		repository.save(new MemberToken(member, purpose, OpaqueTokens.hash(raw), expiresAt, now, event));
		return new IssuedToken(raw, expiresAt);
	}

	/** Returns the member a link belongs to, or throws {@link InvalidMemberTokenException}. */
	@Transactional
	public Member resolve(String raw, MemberTokenPurpose purpose) {
		return use(raw, purpose).getMember();
	}

	/** Returns the member and the one event an attendance link belongs to. */
	@Transactional
	public ResolvedAttendanceToken resolveAttendance(String raw) {
		MemberToken token = use(raw, MemberTokenPurpose.ATTENDANCE);
		return new ResolvedAttendanceToken(token.getMember(), token.getEvent());
	}

	private MemberToken use(String raw, MemberTokenPurpose purpose) {
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
		return token;
	}

	@Transactional
	public void revokeAll(Member member) {
		repository.revokeAllForMember(member.getId(), clock.instant());
	}

}
