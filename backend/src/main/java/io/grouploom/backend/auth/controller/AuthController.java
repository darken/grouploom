package io.grouploom.backend.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.auth.AuthResult;
import io.grouploom.backend.auth.dto.AuthResponse;
import io.grouploom.backend.auth.dto.LoginRequest;
import io.grouploom.backend.auth.dto.SignUpRequest;
import io.grouploom.backend.auth.service.AuthService;
import io.grouploom.backend.config.AuthProperties;
import io.grouploom.backend.error.InvalidRefreshTokenException;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

	static final String REFRESH_COOKIE = "gl_refresh";
	// The servlet path (/api) is part of the URL the browser sees, so the cookie path includes it.
	private static final String COOKIE_PATH = "/api/auth";

	private final AuthService service;
	private final AuthProperties properties;

	public AuthController(AuthService service, AuthProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request) {
		return respond(HttpStatus.CREATED, service.signUp(request));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return respond(HttpStatus.OK, service.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		return respond(HttpStatus.OK, service.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			service.logout(refreshToken);
		}
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookie("", java.time.Duration.ZERO).toString())
			.build();
	}

	private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthResult result) {
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), properties.refreshTtl()).toString())
			.body(result.response());
	}

	private ResponseCookie cookie(String value, java.time.Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)
			.secure(properties.cookieSecure())
			.sameSite("Strict")
			.path(COOKIE_PATH)
			.maxAge(maxAge)
			.build();
	}

}
