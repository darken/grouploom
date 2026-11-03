package io.grouploom.backend.error;

public class ForbiddenException extends DomainException {

	public ForbiddenException(ErrorCode errorCode) {
		super(errorCode);
	}

}
