package io.grouploom.backend.error;

public class InvalidCredentialsException extends DomainException {

	public InvalidCredentialsException() {
		super(ErrorCode.AUTH_INVALID_CREDENTIALS);
	}

}
