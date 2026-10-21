package io.grouploom.backend.auth.service;

import java.time.Clock;
import java.util.Locale;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.AuthResult;
import io.grouploom.backend.auth.dto.AuthResponse;
import io.grouploom.backend.auth.dto.LoginRequest;
import io.grouploom.backend.auth.dto.SignUpRequest;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.InvalidCredentialsException;
import io.grouploom.backend.error.InvalidRefreshTokenException;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.dto.MeResponse;
import io.grouploom.backend.user.repository.UserRepository;

@Service
public class AuthService {

	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
	// Checked when the email is unknown so response time doesn't reveal which emails are registered.
	private final String dummyHash = passwordEncoder.encode("grouploom-dummy-password");

	private final GroupRepository groups;
	private final UserRepository users;
	private final TokenService tokens;
	private final RefreshTokenService refreshTokens;
	private final Clock clock;

	public AuthService(GroupRepository groups, UserRepository users, TokenService tokens,
			RefreshTokenService refreshTokens, Clock clock) {
		this.groups = groups;
		this.users = users;
		this.tokens = tokens;
		this.refreshTokens = refreshTokens;
		this.clock = clock;
	}

	@Transactional
	public AuthResult signUp(SignUpRequest request) {
		String email = normalize(request.email());
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("An account with this email already exists.");
		}
		Group group = groups.save(new Group(request.groupName().trim(), clock.instant()));
		User user = users.save(new User(group, email, request.displayName().trim(),
				passwordEncoder.encode(request.password()), Role.OWNER, clock.instant()));
		return result(user, refreshTokens.issue(user));
	}

	@Transactional
	public AuthResult login(LoginRequest request) {
		User user = users.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
		if (user == null) {
			passwordEncoder.matches(request.password(), dummyHash);
			throw new InvalidCredentialsException();
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return result(user, refreshTokens.issue(user));
	}

	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public AuthResult refresh(String rawRefreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
		return result(rotation.user(), rotation.rawToken());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokens.revoke(rawRefreshToken);
	}

	private AuthResult result(User user, String refreshToken) {
		TokenService.AccessToken access = tokens.issue(user);
		return new AuthResult(new AuthResponse(access.value(), access.expiresIn(), MeResponse.from(user)),
				refreshToken);
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
