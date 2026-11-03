package io.grouploom.backend.error;

public class TooManyRequestsException extends DomainException {

	public TooManyRequestsException(ErrorCode errorCode) {
		super(errorCode);
	}

}
