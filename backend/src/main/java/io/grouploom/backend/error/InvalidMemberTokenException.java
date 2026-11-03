package io.grouploom.backend.error;

public class InvalidMemberTokenException extends DomainException {

	/** {@code errorCode} is {@link ErrorCode#MEMBER_TOKEN_INVALID} or {@link ErrorCode#MEMBER_TOKEN_EXPIRED}. */
	public InvalidMemberTokenException(ErrorCode errorCode) {
		super(errorCode);
	}

}
