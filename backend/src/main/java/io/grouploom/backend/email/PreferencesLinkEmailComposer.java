package io.grouploom.backend.email;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.IssuedToken;
import io.grouploom.backend.membertoken.PreferencesLinks;
import io.grouploom.backend.user.User;

@Component
public class PreferencesLinkEmailComposer implements EmailComposer {

	private final TemplateEngine templates;
	private final AppProperties properties;
	private final MemberTokenService memberTokens;
	private final MessageSource messages;

	public PreferencesLinkEmailComposer(@Qualifier("mailTemplateEngine") TemplateEngine templates,
			AppProperties properties, MemberTokenService memberTokens, MessageSource messages) {
		this.messages = messages;
		this.templates = templates;
		this.properties = properties;
		this.memberTokens = memberTokens;
	}

	@Override
	public Set<EmailKind> kinds() {
		return Set.of(EmailKind.PREFERENCES_LINK);
	}

	@Override
	public OutgoingEmail compose(EmailOutboxEntry entry) {
		// A fresh token per attempt, so nothing stored in the outbox can be used as a link.
		IssuedToken token = memberTokens.issue(entry.getMember(), MemberTokenPurpose.PREFERENCES);
		return compose(entry.getMember(), entry.getGroup(), entry.getRequestedBy(), token);
	}

	/** {@code requestedBy} may be null (the organizer's account is gone); then there is no Reply-To. */
	public OutgoingEmail compose(Member member, Group group, User requestedBy, IssuedToken token) {
		// The group's language, read now so a change also applies to emails already queued.
		Locale locale = group.getLanguage().toLocale();
		String groupName = headerSafe(group.getName());
		String subject = messages.getMessage("mail.preferences.subject", new Object[] { groupName }, locale);

		Context context = new Context(locale);
		context.setVariable("subject", subject);
		context.setVariable("groupName", group.getName());
		context.setVariable("memberName", member.getName());
		context.setVariable("link", PreferencesLinks.url(properties.publicBaseUrl(), token.raw()));
		context.setVariable("ttlText", describe(properties.memberLinks().ttl(), locale));

		return new OutgoingEmail(member.getEmail(), messages.getMessage("mail.sender", new Object[] { groupName }, locale),
				requestedBy == null ? null : requestedBy.getEmail(), subject,
				templates.process("preferences-link.txt", context),
				templates.process("preferences-link.html", context));
	}

	/** Values that end up in headers must never be able to start a new header. */
	static String headerSafe(String value) {
		return value.replace("\r", "").replace("\n", "");
	}

	private String describe(Duration ttl, Locale locale) {
		if (ttl.toMinutes() % (24 * 60) == 0) {
			return plural("days", ttl.toDays(), locale);
		}
		if (ttl.toMinutes() % 60 == 0) {
			return plural("hours", ttl.toHours(), locale);
		}
		return plural("minutes", ttl.toMinutes(), locale);
	}

	// One/other, as the languages we send in need. The number is passed as text so it is never grouped ("1,000").
	private String plural(String unit, long n, Locale locale) {
		String key = "mail.ttl." + unit + (n == 1 ? ".one" : ".other");
		return messages.getMessage(key, new Object[] { String.valueOf(n) }, locale);
	}

}
