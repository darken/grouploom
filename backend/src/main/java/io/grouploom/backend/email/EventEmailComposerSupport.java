package io.grouploom.backend.email;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import io.grouploom.backend.attendance.AttendanceLinks;
import io.grouploom.backend.attendance.AttendanceRepository;
import io.grouploom.backend.attendance.AttendanceResponse;
import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.IssuedToken;
import io.grouploom.backend.membertoken.PreferencesLinks;
import io.grouploom.backend.membertoken.UnsubscribeLinks;

/**
 * What the invitation, reminder and cancellation emails share: the event block, the footer's preferences link and
 * the one-click unsubscribe headers. Each message gets fresh tokens, so nothing stored can be used as a link.
 */
abstract class EventEmailComposerSupport implements EmailComposer {

	private final TemplateEngine templates;
	private final AppProperties properties;
	private final MemberTokenService memberTokens;
	private final AttendanceRepository attendances;
	private final MessageSource messages;

	EventEmailComposerSupport(TemplateEngine templates, AppProperties properties, MemberTokenService memberTokens,
			AttendanceRepository attendances, MessageSource messages) {
		this.messages = messages;
		this.templates = templates;
		this.properties = properties;
		this.memberTokens = memberTokens;
		this.attendances = attendances;
	}

	/** The group's language, read when the email is composed so a change also applies to emails already queued. */
	static Locale localeOf(EmailOutboxEntry entry) {
		return entry.getGroup().getLanguage().toLocale();
	}

	String message(Locale locale, String key, Object... args) {
		return messages.getMessage(key, args, locale);
	}

	/**
	 * {@code template} is the base name, e.g. {@code event-invitation} (rendered as .txt and .html).
	 * {@code includeAttendance} adds the reply block, which depends on the member's answer at send time.
	 */
	OutgoingEmail render(EmailOutboxEntry entry, String subject, String template, String statusLabel, boolean cancelled,
			boolean includeAttendance) {
		Locale locale = localeOf(entry);
		Event event = entry.getEvent();
		String note = entry.getNotification() == null ? null : blankToNull(entry.getNotification().getMessage());
		IssuedToken preferences = memberTokens.issue(entry.getMember(), MemberTokenPurpose.PREFERENCES);
		IssuedToken unsubscribe = memberTokens.issue(entry.getMember(), MemberTokenPurpose.UNSUBSCRIBE);

		Context context = new Context(locale);
		context.setVariable("subject", subject);
		context.setVariable("groupName", entry.getGroup().getName());
		context.setVariable("memberName", entry.getMember().getName());
		context.setVariable("statusLabel", statusLabel);
		context.setVariable("cancelled", cancelled);
		context.setVariable("typeName", event.getEventType().getName());
		context.setVariable("typeColor", hex(event.getEventType().getColor()));
		context.setVariable("title", event.getTitle());
		context.setVariable("timeLine", EventTimeText.range(event.getStartsAt(), event.getEndsAt(), event.getZoneId(), locale));
		context.setVariable("location", blankToNull(event.getLocation()));
		context.setVariable("description", blankToNull(event.getDescription()));
		context.setVariable("note", note);
		context.setVariable("preferencesLink", PreferencesLinks.url(properties.publicBaseUrl(), preferences.raw()));

		if (includeAttendance) {
			addAttendance(context, entry, event);
		}

		Map<String, String> headers = new HashMap<>();
		headers.put("List-Unsubscribe", "<" + UnsubscribeLinks.url(properties.publicBaseUrl(), unsubscribe.raw()) + ">");
		headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");

		String groupName = PreferencesLinkEmailComposer.headerSafe(entry.getGroup().getName());
		var requestedBy = entry.getRequestedBy();
		return new OutgoingEmail(entry.getMember().getEmail(), message(locale, "mail.sender", groupName),
				requestedBy == null ? null : requestedBy.getEmail(), PreferencesLinkEmailComposer.headerSafe(subject),
				templates.process(template + ".txt", context), templates.process(template + ".html", context), headers);
	}

	/** One token per email, bound to the event; members who already answered get a link to change it instead. */
	private void addAttendance(Context context, EmailOutboxEntry entry, Event event) {
		IssuedToken token = memberTokens.issue(entry.getMember(), MemberTokenPurpose.ATTENDANCE, event);
		String base = properties.publicBaseUrl();
		AttendanceResponse current = attendances.findByEventIdAndMemberId(event.getId(), entry.getMember().getId())
			.map(a -> a.getResponse())
			.orElse(null);
		context.setVariable("currentResponse", current == null ? null : current.name());
		context.setVariable("goingLink", AttendanceLinks.url(base, token.raw(), AttendanceResponse.GOING));
		context.setVariable("notGoingLink", AttendanceLinks.url(base, token.raw(), AttendanceResponse.NOT_GOING));
		context.setVariable("changeLink", AttendanceLinks.url(base, token.raw(), null));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	// The same system colors the app uses for event types.
	private static String hex(EventTypeColor color) {
		return switch (color) {
			case BLUE -> "#007aff";
			case GREEN -> "#34c759";
			case ORANGE -> "#ff9500";
			case RED -> "#ff3b30";
			case PURPLE -> "#af52de";
			case PINK -> "#ff2d55";
			case TEAL -> "#30b0c7";
			case GRAY -> "#8e8e93";
		};
	}

}
