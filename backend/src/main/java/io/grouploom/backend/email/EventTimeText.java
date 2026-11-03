package io.grouploom.backend.email;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats event times for emails, always in the event's own zone and in the group's language. Mirrors the
 * frontend's {@code ui/formatEventTime.ts}, except the zone abbreviation is always shown (an email has no viewer
 * zone).
 */
public final class EventTimeText {

	public enum RelativeDay {

		TODAY, TOMORROW, ON_DATE

	}

	private EventTimeText() {
	}

	private static boolean japanese(Locale locale) {
		return Locale.JAPANESE.getLanguage().equals(locale.getLanguage());
	}

	// English "Sat, Nov 2"; Japanese "11月2日(土)".
	private static DateTimeFormatter dateFormat(Locale locale) {
		return DateTimeFormatter.ofPattern(japanese(locale) ? "M月d日(E)" : "EEE, MMM d", locale);
	}

	private static DateTimeFormatter clockFormat(Locale locale) {
		return DateTimeFormatter.ofPattern("HH:mm", locale);
	}

	/**
	 * "Sat, Nov 2 · 10:00 – 12:00 CET", or "Sat, Nov 2, 22:00 – Sun, Nov 3, 02:00 CET" across midnight; in Japanese
	 * "11月2日(土) 10:00〜12:00 JST".
	 */
	public static String range(Instant start, Instant end, ZoneId zone, Locale locale) {
		var s = start.atZone(zone);
		var e = end.atZone(zone);
		DateTimeFormatter date = dateFormat(locale);
		DateTimeFormatter clock = clockFormat(locale);
		boolean sameDay = s.toLocalDate().equals(e.toLocalDate());
		String text;
		if (japanese(locale)) {
			text = sameDay ? date.format(s) + " " + clock.format(s) + "〜" + clock.format(e)
					: date.format(s) + " " + clock.format(s) + "〜" + date.format(e) + " " + clock.format(e);
		}
		else {
			text = sameDay ? date.format(s) + " · " + clock.format(s) + " – " + clock.format(e)
					: date.format(s) + ", " + clock.format(s) + " – " + date.format(e) + ", " + clock.format(e);
		}
		return text + " " + DateTimeFormatter.ofPattern("z", locale).format(e);
	}

	/** "Sat, Nov 2". */
	public static String date(Instant instant, ZoneId zone, Locale locale) {
		return dateFormat(locale).format(instant.atZone(zone));
	}

	/** "10:00". */
	public static String clock(Instant instant, ZoneId zone, Locale locale) {
		return clockFormat(locale).format(instant.atZone(zone));
	}

	/** The event's day relative to when the email is sent; the words for it live in the message bundles. */
	public static RelativeDay relativeDay(Instant start, Instant sendAt, ZoneId zone) {
		LocalDate eventDay = start.atZone(zone).toLocalDate();
		LocalDate sendDay = sendAt.atZone(zone).toLocalDate();
		if (eventDay.equals(sendDay)) {
			return RelativeDay.TODAY;
		}
		if (eventDay.equals(sendDay.plusDays(1))) {
			return RelativeDay.TOMORROW;
		}
		return RelativeDay.ON_DATE;
	}

}
