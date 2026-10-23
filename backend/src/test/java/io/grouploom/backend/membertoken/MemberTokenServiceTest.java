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

	@Test
	void resolve_withTokenOfAnotherPurpose_throwsInvalid() {
		// Only PREFERENCES exists today (Slice 8 adds ATTENDANCE), so simulate a stored token whose purpose differs.
		MemberToken token = stored(NOW.plusSeconds(60));
		ReflectionTestUtils.setField(token, "purpose", null);

		assertThatThrownBy(() -> service.resolve("raw", MemberTokenPurpose.PREFERENCES))
			.isInstanceOfSatisfying(InvalidMemberTokenException.class,
					e -> assertThat(e.getCode()).isEqualTo("member-token.invalid"));
		assertThat(token.getLastUsedAt()).isNull();
	}

	@Test
	void revokeAll_revokesEveryTokenOfTheMember() {
		service.revokeAll(member);

		verify(repository).revokeAllForMember(member.getId(), NOW);
	}

}
