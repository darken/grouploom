package io.grouploom.backend.error;

public class ConflictException extends DomainException {

	private final String field;

	public ConflictException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	/** {@code field} names the input the conflict belongs to, or is null. */
	public ConflictException(ErrorCode errorCode, String field, Object... args) {
		super(errorCode, args);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
