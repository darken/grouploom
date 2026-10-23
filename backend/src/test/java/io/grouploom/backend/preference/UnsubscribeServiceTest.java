package io.grouploom.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;

@ExtendWith(MockitoExtension.class)
class UnsubscribeServiceTest {

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Mock MemberTokenService tokens;
	@Mock MemberPreferencesRepository preferences;

	private Member member;
	private UnsubscribeService service;

	@BeforeEach
	void setUp() {
		member = new Member(new Group("Club", CREATED), "Ana", "ana@example.com", CREATED);
		ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
		service = new UnsubscribeService(tokens, preferences, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void unsubscribe_withoutPreferencesYet_createsThemPaused() {
		given(tokens.resolve("raw", MemberTokenPurpose.UNSUBSCRIBE)).willReturn(member);
		given(preferences.findById(member.getId())).willReturn(Optional.empty());

		service.unsubscribe("raw");

		ArgumentCaptor<MemberPreferences> captor = ArgumentCaptor.forClass(MemberPreferences.class);
		verify(preferences).save(captor.capture());
		assertThat(captor.getValue().isInvitationsPaused()).isTrue();
		assertThat(captor.getValue().getInvitationsPausedAt()).isEqualTo(NOW);
	}

	@Test
	void unsubscribe_withExistingUnpausedPreferences_pausesThem() {
		MemberPreferences existing = new MemberPreferences(member, CREATED);
		given(tokens.resolve("raw", MemberTokenPurpose.UNSUBSCRIBE)).willReturn(member);
		given(preferences.findById(member.getId())).willReturn(Optional.of(existing));

		service.unsubscribe("raw");

		assertThat(existing.getInvitationsPausedAt()).isEqualTo(NOW);
		verify(preferences, never()).save(any());
	}

	@Test
	void unsubscribe_isIdempotentAndKeepsTheOriginalPauseTime() {
		MemberPreferences existing = new MemberPreferences(member, CREATED);
		existing.setInvitationsPaused(true, CREATED);
		given(tokens.resolve("raw", MemberTokenPurpose.UNSUBSCRIBE)).willReturn(member);
		given(preferences.findById(member.getId())).willReturn(Optional.of(existing));

		service.unsubscribe("raw");
		service.unsubscribe("raw");

		assertThat(existing.getInvitationsPausedAt()).isEqualTo(CREATED);
	}

	@Test
	void unsubscribe_withATokenOfAnotherPurpose_isInvalidAndChangesNothing() {
		given(tokens.resolve("preferences-token", MemberTokenPurpose.UNSUBSCRIBE))
			.willThrow(new InvalidMemberTokenException(InvalidMemberTokenException.INVALID));

		assertThatThrownBy(() -> service.unsubscribe("preferences-token")).isInstanceOf(InvalidMemberTokenException.class);

		verify(preferences, never()).save(any());
	}

}
