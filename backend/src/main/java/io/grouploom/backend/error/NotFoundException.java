package io.grouploom.backend.error;

public class NotFoundException extends DomainException {

	public NotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}

}
