package io.grouploom.backend.group;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the lowercase tag ({@code en}), which is what the database check constraint allows. */
@Converter(autoApply = true)
public class LanguageConverter implements AttributeConverter<Language, String> {

	@Override
	public String convertToDatabaseColumn(Language language) {
		return language == null ? null : language.tag();
	}

	@Override
	public Language convertToEntityAttribute(String tag) {
		return tag == null ? null : Language.fromTag(tag);
	}

}
