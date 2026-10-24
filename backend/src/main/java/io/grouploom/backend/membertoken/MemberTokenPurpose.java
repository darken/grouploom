package io.grouploom.backend.membertoken;

public enum MemberTokenPurpose {
	PREFERENCES,
	/** Can only pause invitations; safe to put in a URL query string. */
	UNSUBSCRIBE,
	/** Answers for one event only; the token is bound to it. */
	ATTENDANCE
}
