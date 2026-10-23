package io.grouploom.backend.email;

import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.membertoken.MemberTokenService;

@Component
public class EventCancellationEmailComposer extends EventEmailComposerSupport {

	public EventCancellationEmailComposer(@Qualifier("mailTemplateEngine") TemplateEngine templates,
			AppProperties properties, MemberTokenService memberTokens) {
		super(templates, properties, memberTokens);
	}

	@Override
	public Set<EmailKind> kinds() {
		return Set.of(EmailKind.EVENT_CANCELLATION);
	}

	@Override
	public OutgoingEmail compose(EmailOutboxEntry entry) {
		Event event = entry.getEvent();
		String subject = "Cancelled: " + event.getTitle() + " on " + EventTimeText.date(event.getStartsAt(), event.getZoneId());
		return render(entry, subject, "event-cancellation", "Cancelled", true);
	}

}
