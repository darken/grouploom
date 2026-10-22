package io.grouploom.backend.error;

public class ConflictException extends RuntimeException {

	private final String code;
	private final String field;

	public ConflictException(String message) {
		this(message, null, null);
	}

	/** {@code code} is a stable machine-readable key; {@code field} names the input the conflict belongs to. */
	public ConflictException(String message, String code, String field) {
		super(message);
		this.code = code;
		this.field = field;
	}

	public String getCode() {
		return code;
	}

	public String getField() {
		return field;
	}

}
