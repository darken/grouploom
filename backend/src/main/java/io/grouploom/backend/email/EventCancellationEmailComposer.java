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

@Component
public class EventCancellationEmailComposer extends EventEmailComposerSupport {

	public EventCancellationEmailComposer(@Qualifier("mailTemplateEngine") TemplateEngine templates,
			AppProperties properties, MemberTokenService memberTokens, AttendanceRepository attendances,
			MessageSource messages) {
		super(templates, properties, memberTokens, attendances, messages);
	}

	@Override
	public Set<EmailKind> kinds() {
		return Set.of(EmailKind.EVENT_CANCELLATION);
	}

	@Override
	public OutgoingEmail compose(EmailOutboxEntry entry) {
		Locale locale = localeOf(entry);
		Event event = entry.getEvent();
		String subject = message(locale, "mail.subject.cancellation", event.getTitle(),
				EventTimeText.date(event.getStartsAt(), event.getZoneId(), locale));
		return render(entry, subject, "event-cancellation", message(locale, "mail.status.cancelled"), true, false);
	}

}
