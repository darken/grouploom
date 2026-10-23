package io.grouploom.backend.error;

public class TooManyRequestsException extends RuntimeException {

	private final String code;

	/** {@code code} is a stable machine-readable key the client can react to. */
	public TooManyRequestsException(String message, String code) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}
