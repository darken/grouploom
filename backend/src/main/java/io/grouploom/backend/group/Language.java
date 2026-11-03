package io.grouploom.backend.group;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The languages a group can use. The tag is what the API, the database and the frontend share. */
public enum Language {
	EN("en"),
	JA("ja");

	private final String tag;

	Language(String tag) {
		this.tag = tag;
	}

	@JsonValue
	public String tag() {
		return tag;
	}

	public Locale toLocale() {
		return Locale.forLanguageTag(tag);
	}

	@JsonCreator
	public static Language fromTag(String tag) {
		return Arrays.stream(values())
			.filter(language -> language.tag.equals(tag))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + tag));
	}

}
