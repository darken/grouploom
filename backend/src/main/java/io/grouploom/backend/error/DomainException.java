package io.grouploom.backend.error;

/** An expected failure that is reported to the user in their language: an {@link ErrorCode} plus message arguments. */
public abstract class DomainException extends RuntimeException {

	private final ErrorCode errorCode;

	private final transient Object[] args;

	protected DomainException(ErrorCode errorCode, Object... args) {
		super(errorCode.code());
		this.errorCode = errorCode;
		this.args = args;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	/** The stable machine-readable code. */
	public String getCode() {
		return errorCode.code();
	}

	public Object[] getArgs() {
		return args;
	}

}
