package io.grouploom.backend.email;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
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

	public PreferencesLinkEmailComposer(@Qualifier("mailTemplateEngine") TemplateEngine templates,
			AppProperties properties, MemberTokenService memberTokens) {
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
		String groupName = headerSafe(group.getName());
		String subject = "Choose which " + groupName + " events you hear about";

		Context context = new Context(Locale.ENGLISH);
		context.setVariable("subject", subject);
		context.setVariable("groupName", group.getName());
		context.setVariable("memberName", member.getName());
		context.setVariable("link", PreferencesLinks.url(properties.publicBaseUrl(), token.raw()));
		context.setVariable("ttlText", describe(properties.memberLinks().ttl()));

		return new OutgoingEmail(member.getEmail(), groupName + " via GroupLoom",
				requestedBy == null ? null : requestedBy.getEmail(), subject,
				templates.process("preferences-link.txt", context),
				templates.process("preferences-link.html", context));
	}

	/** Values that end up in headers must never be able to start a new header. */
	static String headerSafe(String value) {
		return value.replace("\r", "").replace("\n", "");
	}

	private static String describe(Duration ttl) {
		if (ttl.toMinutes() % (24 * 60) == 0) {
			return plural(ttl.toDays(), "day");
		}
		if (ttl.toMinutes() % 60 == 0) {
			return plural(ttl.toHours(), "hour");
		}
		return plural(ttl.toMinutes(), "minute");
	}

	private static String plural(long n, String unit) {
		return n + " " + unit + (n == 1 ? "" : "s");
	}

}
