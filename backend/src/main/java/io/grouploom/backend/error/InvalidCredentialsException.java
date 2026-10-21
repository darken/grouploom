package io.grouploom.backend.error;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Email or password is incorrect.");
	}

}
