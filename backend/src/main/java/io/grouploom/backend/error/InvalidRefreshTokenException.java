package io.grouploom.backend.error;

public class InvalidRefreshTokenException extends DomainException {

	public InvalidRefreshTokenException() {
		super(ErrorCode.AUTH_SESSION_EXPIRED);
	}

}
