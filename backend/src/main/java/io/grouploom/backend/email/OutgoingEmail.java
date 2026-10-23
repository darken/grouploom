package io.grouploom.backend.email;

/** A fully rendered message. {@code replyTo} may be null. */
public record OutgoingEmail(String to, String fromName, String replyTo, String subject, String text, String html) {
}
