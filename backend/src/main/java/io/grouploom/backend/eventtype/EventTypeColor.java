package io.grouploom.backend.eventtype;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EventTypeColor {
	BLUE, GREEN, ORANGE, RED, PURPLE, PINK, TEAL, GRAY;

	/** The lowercase key stored in the database and exposed in the API. */
	@JsonValue
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	@JsonCreator
	public static EventTypeColor fromKey(String key) {
		return Arrays.stream(values()).filter(c -> c.key().equals(key)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown color: " + key));
	}
}
