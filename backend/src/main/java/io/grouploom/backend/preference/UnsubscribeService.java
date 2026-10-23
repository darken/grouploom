package io.grouploom.backend.preference;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;

/** One-click unsubscribe from event emails: authorized by an {@code UNSUBSCRIBE} token, which can only pause invitations. */
@Service
public class UnsubscribeService {

	private final MemberTokenService tokens;
	private final MemberPreferencesRepository preferences;
	private final Clock clock;

	public UnsubscribeService(MemberTokenService tokens, MemberPreferencesRepository preferences, Clock clock) {
		this.tokens = tokens;
		this.preferences = preferences;
		this.clock = clock;
	}

	@Transactional
	public void unsubscribe(String rawToken) {
		Member member = tokens.resolve(rawToken, MemberTokenPurpose.UNSUBSCRIBE);
		Instant now = clock.instant();
		var existing = preferences.findById(member.getId());
		if (existing.isPresent()) {
			existing.get().setInvitationsPaused(true, now);
			return;
		}
		MemberPreferences created = new MemberPreferences(member, now);
		created.setInvitationsPaused(true, now);
		preferences.save(created);
	}

}
