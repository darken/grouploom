package io.grouploom.backend.preference;

/** Hides most of an address on public pages, e.g. {@code a••@example.com}. */
public final class EmailMasker {

	private static final String BULLETS = "••";

	private EmailMasker() {
	}

	public static String mask(String email) {
		int at = email.lastIndexOf('@');
		if (at <= 0) {
			return BULLETS;
		}
		String domain = email.substring(at);
		int firstLength = Character.charCount(email.codePointAt(0));
		if (email.codePointCount(0, at) == 1) {
			return "•" + domain;
		}
		return email.substring(0, firstLength) + BULLETS + domain;
	}

}
