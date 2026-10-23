package io.grouploom.backend.membertoken;

public enum MemberTokenPurpose {
	PREFERENCES,
	/** Can only pause invitations; safe to put in a URL query string. */
	UNSUBSCRIBE
}
