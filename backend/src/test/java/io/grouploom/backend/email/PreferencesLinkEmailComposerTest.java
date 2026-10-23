package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

import io.grouploom.backend.config.MailTemplateConfig;
import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenService.IssuedToken;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;

class PreferencesLinkEmailComposerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final IssuedToken TOKEN = new IssuedToken("tok_abc-123", NOW.plus(Duration.ofDays(60)));
	private static final String LINK = "https://app.example.com/m/preferences#t=tok_abc-123";

	private final TemplateEngine engine = new MailTemplateConfig().mailTemplateEngine();
	private final PreferencesLinkEmailComposer composer = new PreferencesLinkEmailComposer(engine,
			TestProperties.app("https://app.example.com/", Duration.ofDays(60)));

	private OutgoingEmail compose(String groupName, String memberName) {
		Group group = new Group(groupName, NOW);
		Member member = new Member(group, memberName, "ana@example.com", NOW);
		User organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, NOW);
		return composer.compose(member, group, organizer, TOKEN);
	}

	@Test
	void compose_setsRecipientSubjectSenderAndReplyTo() {
		OutgoingEmail email = compose("Chess Club", "Ana");

		assertThat(email.to()).isEqualTo("ana@example.com");
		assertThat(email.subject()).isEqualTo("Choose which Chess Club events you hear about");
		assertThat(email.fromName()).isEqualTo("Chess Club via GroupLoom");
		assertThat(email.replyTo()).isEqualTo("olga@example.com");
	}

	@Test
	void compose_withoutRequester_hasNoReplyTo() {
		Group group = new Group("Chess Club", NOW);
		Member member = new Member(group, "Ana", "ana@example.com", NOW);

		OutgoingEmail email = composer.compose(member, group, null, TOKEN);

		assertThat(email.replyTo()).isNull();
	}

	@Test
	void compose_includesLinkWithFragmentTtlAndGreetingInBothParts() {
		OutgoingEmail email = compose("Chess Club", "Ana");

		for (String body : new String[] { email.text(), email.html() }) {
			assertThat(body).contains(LINK);
			assertThat(body).contains("This link works for 60 days.");
			assertThat(body).contains("Ana");
			assertThat(body).contains("you can ignore it.");
		}
		assertThat(email.html()).contains("Set your preferences");
		assertThat(email.text()).contains("Set your preferences");
		assertThat(email.text()).doesNotContain("<");
	}

	@Test
	void compose_htmlOffersLightAndDarkColorSchemes() {
		OutgoingEmail email = compose("Chess Club", "Ana");

		assertThat(email.html()).contains("<meta name=\"color-scheme\" content=\"light dark\"");
		assertThat(email.html()).contains("prefers-color-scheme: dark");
		assertThat(email.html()).doesNotContain("<img");
	}

	@Test
	void compose_escapesGroupAndMemberNamesInHtml() {
		OutgoingEmail email = compose("<script>alert(1)</script>", "<b onmouseover=x>Ana</b>");

		assertThat(email.html()).doesNotContain("<script>alert(1)</script>");
		assertThat(email.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
		assertThat(email.html()).doesNotContain("<b onmouseover=x>");
		assertThat(email.html()).contains("&lt;b onmouseover=x&gt;Ana&lt;/b&gt;");
	}

	@Test
	void compose_stripsCarriageReturnsAndLineFeedsFromHeaderValues() {
		OutgoingEmail email = compose("Chess\r\nBcc: evil@example.com", "Ana");

		assertThat(email.subject()).doesNotContain("\r").doesNotContain("\n");
		assertThat(email.fromName()).doesNotContain("\r").doesNotContain("\n");
		assertThat(email.subject()).isEqualTo("Choose which ChessBcc: evil@example.com events you hear about");
	}

	@Test
	void compose_formatsTtlInHoursWhenNotWholeDays() {
		PreferencesLinkEmailComposer shortLived = new PreferencesLinkEmailComposer(engine,
				TestProperties.app("https://app.example.com", Duration.ofHours(36)));
		Group group = new Group("Chess Club", NOW);
		Member member = new Member(group, "Ana", "ana@example.com", NOW);

		OutgoingEmail email = shortLived.compose(member, group, null, TOKEN);

		assertThat(email.text()).contains("This link works for 36 hours.");
	}

}
