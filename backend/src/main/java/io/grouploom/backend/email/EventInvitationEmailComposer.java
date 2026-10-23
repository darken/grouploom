package io.grouploom.backend.email;

import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.membertoken.MemberTokenService;

/** Invitations and reminders differ only in wording. */
@Component
public class EventInvitationEmailComposer extends EventEmailComposerSupport {

	public EventInvitationEmailComposer(@Qualifier("mailTemplateEngine") TemplateEngine templates,
			AppProperties properties, MemberTokenService memberTokens) {
		super(templates, properties, memberTokens);
	}

	@Override
	public Set<EmailKind> kinds() {
		return Set.of(EmailKind.EVENT_INVITATION, EmailKind.EVENT_REMINDER);
	}

	@Override
	public OutgoingEmail compose(EmailOutboxEntry entry) {
		Event event = entry.getEvent();
		var zone = event.getZoneId();
		if (entry.getKind() == EmailKind.EVENT_REMINDER) {
			String day = EventTimeText.dayWord(event.getStartsAt(), entry.getNotification().getSendAt(), zone);
			String subject = "Reminder: " + event.getTitle() + " is " + day + " at "
					+ EventTimeText.clock(event.getStartsAt(), zone);
			return render(entry, subject, "event-reminder", "Reminder", false);
		}
		String subject = event.getTitle() + " · " + EventTimeText.date(event.getStartsAt(), zone) + ", "
				+ EventTimeText.clock(event.getStartsAt(), zone);
		return render(entry, subject, "event-invitation", "You're invited", false);
	}

}
