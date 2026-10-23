package io.grouploom.backend.error;

public class InvalidMemberTokenException extends RuntimeException {

	public static final String INVALID = "member-token.invalid";
	public static final String EXPIRED = "member-token.expired";

	private final String code;

	/** {@code code} is {@link #INVALID} or {@link #EXPIRED}; the public page maps it to its message. */
	public InvalidMemberTokenException(String code) {
		super(EXPIRED.equals(code) ? "This link has expired." : "This link isn't valid.");
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}
