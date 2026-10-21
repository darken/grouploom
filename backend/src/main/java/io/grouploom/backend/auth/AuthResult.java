package io.grouploom.backend.auth;

import io.grouploom.backend.auth.dto.AuthResponse;

/** An auth response body plus the raw refresh token to be sent as a cookie. */
public record AuthResult(AuthResponse response, String refreshToken) {
}
