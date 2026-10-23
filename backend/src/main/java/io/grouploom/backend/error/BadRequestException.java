package io.grouploom.backend.error;

public class BadRequestException extends RuntimeException {

	private final String code;
	private final String field;

	/** {@code code} is a stable machine-readable key; {@code field} names the input the problem belongs to. */
	public BadRequestException(String message, String code, String field) {
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
