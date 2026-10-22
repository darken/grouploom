package io.grouploom.backend.eventtype;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EventTypeColorConverter implements AttributeConverter<EventTypeColor, String> {

	@Override
	public String convertToDatabaseColumn(EventTypeColor color) {
		return color == null ? null : color.key();
	}

	@Override
	public EventTypeColor convertToEntityAttribute(String key) {
		return key == null ? null : EventTypeColor.fromKey(key);
	}

}
