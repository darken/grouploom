package io.grouploom.backend.error;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Session expired. Please sign in again.");
	}

}
