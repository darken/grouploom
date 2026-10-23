package io.grouploom.backend.email;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats event times for emails, always in the event's own zone and in English. Mirrors the frontend's
 * {@code ui/formatEventTime.ts}, except the zone abbreviation is always shown (an email has no viewer zone).
 */
public final class EventTimeText {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("z", Locale.ENGLISH);

	private EventTimeText() {
	}

	/** "Sat, Nov 2 · 10:00 – 12:00 CET", or "Sat, Nov 2, 22:00 – Sun, Nov 3, 02:00 CET" across midnight. */
	public static String range(Instant start, Instant end, ZoneId zone) {
		var s = start.atZone(zone);
		var e = end.atZone(zone);
		String text = s.toLocalDate().equals(e.toLocalDate())
				? DATE.format(s) + " · " + CLOCK.format(s) + " – " + CLOCK.format(e)
				: DATE.format(s) + ", " + CLOCK.format(s) + " – " + DATE.format(e) + ", " + CLOCK.format(e);
		return text + " " + ZONE.format(e);
	}

	/** "Sat, Nov 2". */
	public static String date(Instant instant, ZoneId zone) {
		return DATE.format(instant.atZone(zone));
	}

	/** "10:00". */
	public static String clock(Instant instant, ZoneId zone) {
		return CLOCK.format(instant.atZone(zone));
	}

	/** "today", "tomorrow" or "on Sat, Nov 2": the event's day relative to when the email is sent. */
	public static String dayWord(Instant start, Instant sendAt, ZoneId zone) {
		LocalDate eventDay = start.atZone(zone).toLocalDate();
		LocalDate sendDay = sendAt.atZone(zone).toLocalDate();
		if (eventDay.equals(sendDay)) {
			return "today";
		}
		if (eventDay.equals(sendDay.plusDays(1))) {
			return "tomorrow";
		}
		return "on " + date(start, zone);
	}

}
