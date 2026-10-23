package io.grouploom.backend.event;

import java.util.Locale;

public enum When {

	UPCOMING, PAST;

	/** Parses the {@code when} query parameter ("upcoming" or "past", any case); null when it is neither. */
	public static When parse(String value) {
		if (value == null) {
			return null;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "upcoming" -> UPCOMING;
			case "past" -> PAST;
			default -> null;
		};
	}

}
