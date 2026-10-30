package io.grouploom.backend.membertoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
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

import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.security.OpaqueTokens;

@ExtendWith(MockitoExtension.class)
class MemberTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Duration TTL = Duration.ofDays(60);

	@Mock MemberTokenRepository repository;

	private MemberTokenService service;
	private Member member;

	@BeforeEach
	void setUp() {
		service = new MemberTokenService(repository,
				TestProperties.app("http://localhost:5173", TTL),
				Clock.fixed(NOW, ZoneOffset.UTC));
		member = new Member(new Group("Club", NOW), "Ana", "ana@example.com", NOW);
		ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
	}

	private MemberToken stored(Instant expiresAt) {
		MemberToken token = new MemberToken(member, MemberTokenPurpose.PREFERENCES, OpaqueTokens.hash("raw"),
				expiresAt, NOW.minusSeconds(60));
		given(repository.findByTokenHash(OpaqueTokens.hash("raw"))).willReturn(Optional.of(token));
		return token;
	}

	@Test
	void issue_storesOnlyTheHashAndExpiresAfterTheConfiguredTtl() {
		var issued = service.issue(member, MemberTokenPurpose.PREFERENCES);

		ArgumentCaptor<MemberToken> captor = ArgumentCaptor.forClass(MemberToken.class);
		verify(repository).save(captor.capture());
		MemberToken saved = captor.getValue();
		assertThat(issued.raw()).hasSize(43);
		assertThat(saved.getTokenHash()).isEqualTo(OpaqueTokens.hash(issued.raw())).isNotEqualTo(issued.raw());
		assertThat(saved.getMember()).isSameAs(member);
		assertThat(saved.getPurpose()).isEqualTo(MemberTokenPurpose.PREFERENCES);
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(TTL)).isEqualTo(issued.expiresAt());
	}

	@Test
	void issue_calledTwice_createsDistinctTokens() {
		var first = service.issue(member, MemberTokenPurpose.PREFERENCES);
		var second = service.issue(member, MemberTokenPurpose.PREFERENCES);

		assertThat(first.raw()).isNotEqualTo(second.raw());
	}

	@Test
	void resolve_withValidToken_returnsMemberAndMarksItUsed() {
		MemberToken token = stored(NOW.plusSeconds(60));

		Member resolved = service.resolve("raw", MemberTokenPurpose.PREFERENCES);

		assertThat(resolved).isSameAs(member);
		assertThat(token.getLastUsedAt()).isEqualTo(NOW);
	}

	@Test
	void resolve_withExpiredToken_throwsExpired() {
		MemberToken token = stored(NOW.minusSeconds(1));

		assertThatThrownBy(() -> service.resolve("raw", MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.expired"));
		assertThat(token.getLastUsedAt()).isNull();
	}

	@Test
	void resolve_withTokenExpiringExactlyNow_throwsExpired() {
		stored(NOW);

		assertThatThrownBy(() -> service.resolve("raw", MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.expired"));
	}

	@Test
	void resolve_withRevokedToken_throwsInvalidEvenWhenAlsoExpired() {
		MemberToken token = stored(NOW.minusSeconds(1));
		token.revoke(NOW.minusSeconds(30));

		assertThatThrownBy(() -> service.resolve("raw", MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.invalid"));
	}

	@Test
	void resolve_withUnknownToken_throwsInvalid() {
		given(repository.findByTokenHash(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.resolve("nope", MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.invalid"));
	}

	@Test
	void resolve_withNullOrBlankToken_throwsInvalidWithoutLookingItUp() {
		assertThatThrownBy(() -> service.resolve(null, MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.invalid"));
		assertThatThrownBy(() -> service.resolve("  ", MemberTokenPurpose.PREFERENCES))
			.isInstanceOf(InvalidMemberTokenException.class);
		org.mockito.Mockito.verifyNoInteractions(repository);
	}

	private Event event() {
		Group group = member.getGroup();
		EventType type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW);
		Event event = new Event(group, type, "Practice", null, null, NOW.plusSeconds(3600), NOW.plusSeconds(7200), "UTC", NOW);
		ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
		return event;
	}

	private MemberToken storedAttendance(Event event) {
		MemberToken token = new MemberToken(member, MemberTokenPurpose.ATTENDANCE, OpaqueTokens.hash("raw"),
				NOW.plusSeconds(60), NOW.minusSeconds(60), event);
		given(repository.findByTokenHash(OpaqueTokens.hash("raw"))).willReturn(Optional.of(token));
		return token;
	}

	@Test
	void issue_attendanceToken_storesTheEventItAnswersFor() {
		Event event = event();

		var issued = service.issue(member, MemberTokenPurpose.ATTENDANCE, event);

		ArgumentCaptor<MemberToken> captor = ArgumentCaptor.forClass(MemberToken.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getPurpose()).isEqualTo(MemberTokenPurpose.ATTENDANCE);
		assertThat(captor.getValue().getEvent()).isSameAs(event);
		assertThat(captor.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(issued.raw()));
		assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
	}

	@Test
	void issue_withoutEvent_storesNoEvent() {
		service.issue(member, MemberTokenPurpose.PREFERENCES);

		ArgumentCaptor<MemberToken> captor = ArgumentCaptor.forClass(MemberToken.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getEvent()).isNull();
	}

	@Test
	void resolveAttendance_withValidToken_returnsMemberAndEventAndMarksItUsed() {
		Event event = event();
		MemberToken token = storedAttendance(event);

		var resolved = service.resolveAttendance("raw");

		assertThat(resolved.member()).isSameAs(member);
		assertThat(resolved.event()).isSameAs(event);
		assertThat(token.getLastUsedAt()).isEqualTo(NOW);
	}

	@Test
	void resolveAttendance_withPreferencesToken_throwsInvalid() {
		stored(NOW.plusSeconds(60));

		assertThatThrownBy(() -> service.resolveAttendance("raw"))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.invalid"));
	}

	@Test
	void resolve_withAttendanceToken_throwsInvalid() {
		MemberToken token = storedAttendance(event());

		assertThatThrownBy(() -> service.resolve("raw", MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.invalid"));
		assertThat(token.getLastUsedAt()).isNull();
	}

	@Test
	void resolveAttendance_withExpiredToken_throwsExpired() {
		MemberToken token = new MemberToken(member, MemberTokenPurpose.ATTENDANCE, OpaqueTokens.hash("raw"),
				NOW.minusSeconds(1), NOW.minusSeconds(60), event());
		given(repository.findByTokenHash(OpaqueTokens.hash("raw"))).willReturn(Optional.of(token));

		assertThatThrownBy(() -> service.resolveAttendance("raw"))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.expired"));
	}

	@Test
	void revokeAll_revokesEveryTokenOfTheMember() {
		service.revokeAll(member);

		verify(repository).revokeAllForMember(member.getId(), NOW);
	}

}
