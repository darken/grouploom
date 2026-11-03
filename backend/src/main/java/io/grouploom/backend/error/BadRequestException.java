package io.grouploom.backend.error;

public class BadRequestException extends DomainException {

	private final String field;

	/** {@code field} names the input the problem belongs to, or is null when no single input is at fault. */
	public BadRequestException(ErrorCode errorCode, String field, Object... args) {
		super(errorCode, args);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
