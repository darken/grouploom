package io.grouploom.backend.email;

import java.util.Map;

/** A fully rendered message. {@code replyTo} may be null; {@code headers} are extra headers (never null). */
public record OutgoingEmail(String to, String fromName, String replyTo, String subject, String text, String html,
		Map<String, String> headers) {

	public OutgoingEmail {
		headers = headers == null ? Map.of() : Map.copyOf(headers);
	}

	public OutgoingEmail(String to, String fromName, String replyTo, String subject, String text, String html) {
		this(to, fromName, replyTo, subject, text, html, Map.of());
	}

}
