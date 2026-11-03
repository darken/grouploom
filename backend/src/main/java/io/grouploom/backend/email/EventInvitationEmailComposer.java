package io.grouploom.backend.email;

import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import io.grouploom.backend.attendance.AttendanceRepository;
import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.membertoken.MemberTokenService;

/** Invitations and reminders differ only in wording. */
@Component
public class EventInvitationEmailComposer extends EventEmailComposerSupport {

	public EventInvitationEmailComposer(@Qualifier("mailTemplateEngine") TemplateEngine templates,
			AppProperties properties, MemberTokenService memberTokens, AttendanceRepository attendances,
			MessageSource messages) {
		super(templates, properties, memberTokens, attendances, messages);
	}

	@Override
	public Set<EmailKind> kinds() {
		return Set.of(EmailKind.EVENT_INVITATION, EmailKind.EVENT_REMINDER);
	}

	@Override
	public OutgoingEmail compose(EmailOutboxEntry entry) {
		Locale locale = localeOf(entry);
		Event event = entry.getEvent();
		var zone = event.getZoneId();
		String clock = EventTimeText.clock(event.getStartsAt(), zone, locale);
		if (entry.getKind() == EmailKind.EVENT_REMINDER) {
			String day = switch (EventTimeText.relativeDay(event.getStartsAt(), entry.getNotification().getSendAt(), zone)) {
				case TODAY -> message(locale, "mail.day.today");
				case TOMORROW -> message(locale, "mail.day.tomorrow");
				case ON_DATE -> message(locale, "mail.day.on", EventTimeText.date(event.getStartsAt(), zone, locale));
			};
			String subject = message(locale, "mail.subject.reminder", event.getTitle(), day, clock);
			return render(entry, subject, "event-reminder", message(locale, "mail.status.reminder"), false, true);
		}
		String subject = message(locale, "mail.subject.invitation", event.getTitle(),
				EventTimeText.date(event.getStartsAt(), zone, locale), clock);
		return render(entry, subject, "event-invitation", message(locale, "mail.status.invited"), false, true);
	}

}
