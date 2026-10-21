package io.grouploom.backend.auth;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.auth.dto.LoginRequest;
import io.grouploom.backend.auth.dto.SignUpRequest;
import io.grouploom.backend.auth.service.AuthService;
import io.grouploom.backend.auth.service.RefreshTokenService;
import io.grouploom.backend.auth.service.TokenService;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.InvalidCredentialsException;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock GroupRepository groups;
	@Mock UserRepository users;
	@Mock TokenService tokens;
	@Mock RefreshTokenService refreshTokens;

	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(groups, users, tokens, refreshTokens, Clock.fixed(NOW, ZoneOffset.UTC));
		given(groups.save(any(Group.class))).willAnswer(i -> {
			Group g = i.getArgument(0);
			ReflectionTestUtils.setField(g, "id", UUID.randomUUID());
			return g;
		});
		given(users.save(any(User.class))).willAnswer(i -> {
			User u = i.getArgument(0);
			ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
			return u;
		});
		given(tokens.issue(any())).willReturn(new TokenService.AccessToken("jwt", 900));
		given(refreshTokens.issue(any())).willReturn("refresh");
	}

	@Test
	void signUp_withNewEmail_createsGroupAndOwnerWithHashedPassword() {
		var result = service.signUp(new SignUpRequest("Chess Club", "Ada", "ada@example.com", "correct horse battery"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(users).save(captor.capture());
		User user = captor.getValue();
		assertThat(user.getRole()).isEqualTo(Role.OWNER);
		assertThat(user.getGroup().getName()).isEqualTo("Chess Club");
		assertThat(user.getPasswordHash()).isNotEqualTo("correct horse battery");
		assertThat(encoder.matches("correct horse battery", user.getPasswordHash())).isTrue();
		assertThat(result.refreshToken()).isEqualTo("refresh");
		assertThat(result.response().accessToken()).isEqualTo("jwt");
	}

	@Test
	void signUp_withMixedCaseEmail_normalizesEmail() {
		service.signUp(new SignUpRequest("Club", "Ada", "  Ada@Example.COM ", "correct horse battery"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(users).save(captor.capture());
		assertThat(captor.getValue().getEmail()).isEqualTo("ada@example.com");
	}

	@Test
	void signUp_withExistingEmail_throwsConflict() {
		given(users.existsByEmailIgnoreCase("ada@example.com")).willReturn(true);

		assertThatThrownBy(() -> service.signUp(new SignUpRequest("Club", "Ada", "ADA@example.com", "correct horse battery")))
			.isInstanceOf(ConflictException.class);
		verify(groups, never()).save(any());
	}

	@Test
	void login_withWrongPassword_throwsInvalidCredentials() {
		given(users.findByEmailIgnoreCase("ada@example.com")).willReturn(Optional.of(existingUser()));

		assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "wrong")))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Email or password is incorrect.");
	}

	@Test
	void login_withUnknownEmail_throwsSameInvalidCredentials() {
		given(users.findByEmailIgnoreCase("nobody@example.com")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "whatever")))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Email or password is incorrect.");
	}

	@Test
	void login_withCorrectPassword_returnsTokens() {
		given(users.findByEmailIgnoreCase("ada@example.com")).willReturn(Optional.of(existingUser()));

		var result = service.login(new LoginRequest(" Ada@example.com", "correct horse battery"));

		assertThat(result.response().user().email()).isEqualTo("ada@example.com");
		assertThat(result.refreshToken()).isEqualTo("refresh");
	}

	private User existingUser() {
		Group group = new Group("Club", NOW);
		ReflectionTestUtils.setField(group, "id", UUID.randomUUID());
		User user = new User(group, "ada@example.com", "Ada", encoder.encode("correct horse battery"), Role.OWNER, NOW);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}

}
