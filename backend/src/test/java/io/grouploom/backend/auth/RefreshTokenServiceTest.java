package io.grouploom.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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

import io.grouploom.backend.auth.repository.RefreshTokenRepository;
import io.grouploom.backend.auth.service.RefreshTokenService;
import io.grouploom.backend.config.AuthProperties;
import io.grouploom.backend.error.InvalidRefreshTokenException;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock RefreshTokenRepository repository;

	private RefreshTokenService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new RefreshTokenService(repository, new AuthProperties(Duration.ofDays(14), true),
				Clock.fixed(NOW, ZoneOffset.UTC));
		Group group = new Group("Club", NOW);
		user = new User(group, "ada@example.com", "Ada", "hash", Role.OWNER, NOW);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
	}

	@Test
	void issue_storesOnlyTheHashOfTheRawToken() {
		String raw = service.issue(user);

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(repository).save(captor.capture());
		assertThat(raw).hasSize(43);
		assertThat(captor.getValue().getTokenHash()).hasSize(64).isNotEqualTo(raw);
		assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
	}

	@Test
	void rotate_withValidToken_revokesOldAndIssuesNew() {
		RefreshToken stored = new RefreshToken(user, "x".repeat(64), NOW.plusSeconds(60), NOW.minusSeconds(60));
		given(repository.findByTokenHash(any())).willReturn(Optional.of(stored));

		var rotation = service.rotate("raw");

		assertThat(stored.isRevoked()).isTrue();
		assertThat(rotation.rawToken()).isNotEqualTo("raw").hasSize(43);
		assertThat(rotation.user()).isSameAs(user);
		verify(repository).save(any(RefreshToken.class));
	}

	@Test
	void rotate_withExpiredToken_throwsWithoutRevokingEverything() {
		RefreshToken stored = new RefreshToken(user, "x".repeat(64), NOW.minusSeconds(1), NOW.minusSeconds(100));
		given(repository.findByTokenHash(any())).willReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
		verify(repository, never()).revokeAllForUser(any(), any());
	}

	@Test
	void rotate_withUnknownToken_throws() {
		given(repository.findByTokenHash(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void rotate_withRevokedToken_revokesAllUserTokensAndThrows() {
		RefreshToken stored = new RefreshToken(user, "x".repeat(64), NOW.plusSeconds(60), NOW.minusSeconds(60));
		stored.revoke(NOW.minusSeconds(10));
		given(repository.findByTokenHash(any())).willReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
		verify(repository).revokeAllForUser(eq(user.getId()), eq(NOW));
	}

}
