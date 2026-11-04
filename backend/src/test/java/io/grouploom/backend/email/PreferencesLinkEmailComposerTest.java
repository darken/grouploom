package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

import io.grouploom.backend.config.MailTemplateConfig;
import io.grouploom.backend.config.TestMessages;
import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.Language;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.IssuedToken;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;

class PreferencesLinkEmailComposerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final IssuedToken TOKEN = new IssuedToken("tok_abc-123", NOW.plus(Duration.ofDays(60)));
	private static final String LINK = "https://app.example.com/m/preferences#t=tok_abc-123";

	private final TemplateEngine engine = new MailTemplateConfig().mailTemplateEngine(TestMessages.source());
	private final MemberTokenService memberTokens = mock(MemberTokenService.class);
	private final PreferencesLinkEmailComposer composer = new PreferencesLinkEmailComposer(engine,
			TestProperties.app("https://app.example.com/", Duration.ofDays(60)), memberTokens, TestMessages.source());

	private OutgoingEmail compose(String groupName, String memberName) {
		Group group = new Group(groupName, Language.EN, NOW);
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
		Group group = new Group("Chess Club", Language.EN, NOW);
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
	void compose_textPartPrintsNamesAsWritten() {
		OutgoingEmail email = compose("Tom's Club", "Ana & <Bo>");

		assertThat(email.text()).startsWith("Tom's Club\n").contains("Ana & <Bo>").contains("Tom's Club would like to know")
			.contains(LINK).doesNotContain("&amp;").doesNotContain("&#39;").doesNotContain("&lt;");
	}

	@Test
	void compose_htmlPartStillEscapesNames() {
		OutgoingEmail email = compose("Tom's Club", "Ana & <Bo>");

		assertThat(email.html()).contains("Ana &amp; &lt;Bo&gt;").containsAnyOf("Tom&#39;s Club", "Tom&#x27;s Club")
			.doesNotContain("<Bo>").doesNotContain("Ana & <Bo>");
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
				TestProperties.app("https://app.example.com", Duration.ofHours(36)), memberTokens, TestMessages.source());
		Group group = new Group("Chess Club", Language.EN, NOW);
		Member member = new Member(group, "Ana", "ana@example.com", NOW);

		OutgoingEmail email = shortLived.compose(member, group, null, TOKEN);

		assertThat(email.text()).contains("This link works for 36 hours.");
	}

	@Test
	void compose_fromAnEntry_issuesAPreferencesTokenAndBuildsTheLink() {
		Group group = new Group("Chess Club", Language.EN, NOW);
		Member member = new Member(group, "Ana", "ana@example.com", NOW);
		User organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, NOW);
		given(memberTokens.issue(member, MemberTokenPurpose.PREFERENCES)).willReturn(TOKEN);
		EmailOutboxEntry entry = new EmailOutboxEntry(group, member, organizer, EmailKind.PREFERENCES_LINK, NOW);

		OutgoingEmail email = composer.compose(entry);

		assertThat(composer.kinds()).containsExactly(EmailKind.PREFERENCES_LINK);
		assertThat(email.text()).contains(LINK);
	}

	@Test
	void compose_ttlUsesTheSingularForOne() {
		for (Duration ttl : new Duration[] { Duration.ofDays(1), Duration.ofHours(1), Duration.ofMinutes(1) }) {
			PreferencesLinkEmailComposer composer = new PreferencesLinkEmailComposer(engine,
					TestProperties.app("https://app.example.com", ttl), memberTokens, TestMessages.source());
			Group group = new Group("Chess Club", Language.EN, NOW);
			Member member = new Member(group, "Ana", "ana@example.com", NOW);

			String text = composer.compose(member, group, null, TOKEN).text();

			assertThat(text).containsAnyOf("works for 1 day.", "works for 1 hour.", "works for 1 minute.");
		}
	}

	// --- Japanese ---

	@Nested
	class Japanese {

		private OutgoingEmail composeJapanese(String groupName, String memberName, Duration ttl) {
			PreferencesLinkEmailComposer composer = new PreferencesLinkEmailComposer(engine,
					TestProperties.app("https://app.example.com/", ttl), memberTokens, TestMessages.source());
			Group group = new Group(groupName, Language.JA, NOW);
			Member member = new Member(group, memberName, "ana@example.com", NOW);
			User organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, NOW);
			return composer.compose(member, group, organizer, TOKEN);
		}

		@Test
		void compose_hasAJapaneseSubjectSenderAndLanguage() {
			OutgoingEmail email = composeJapanese("Chess Club", "Ana", Duration.ofDays(60));

			assertThat(email.subject()).isEqualTo("Chess Clubから届くイベントを選んでください");
			assertThat(email.fromName()).isEqualTo("Chess Club(GroupLoom経由)");
			assertThat(email.html()).contains("lang=\"ja\"").doesNotContain("lang=\"en\"");
		}

		@Test
		void compose_isJapaneseInBothParts() {
			OutgoingEmail email = composeJapanese("Chess Club", "Ana", Duration.ofDays(7));

			for (String body : new String[] { email.text(), email.html() }) {
				assertThat(body).contains(LINK).contains("Anaさん、").contains("Chess Clubでは、")
					.contains("受け取る内容を設定する").contains("このリンクは7日間有効です。")
					.contains("このメールに心当たりがない場合は、無視してください。");
				assertThat(body).doesNotContain("??").doesNotContain("Set your preferences").doesNotContain("Hi ");
			}
			assertThat(email.html()).contains("下のボタンから").contains("または、このリンクをブラウザにコピーしてください：");
			assertThat(email.text()).contains("下のリンクから").contains("受け取る内容を設定する:");
		}

		@Test
		void compose_htmlFontStackFallsBackToJapaneseFonts() {
			assertThat(composeJapanese("Chess Club", "Ana", Duration.ofDays(7)).html()).contains("'Hiragino Sans'")
				.contains("'Yu Gothic'").contains("Meiryo");
		}

		@Test
		void compose_describesTheTtlInJapanese() {
			assertThat(composeJapanese("Chess Club", "Ana", Duration.ofHours(36)).text()).contains("このリンクは36時間有効です。");
			assertThat(composeJapanese("Chess Club", "Ana", Duration.ofMinutes(90)).text()).contains("このリンクは90分間有効です。");
			assertThat(composeJapanese("Chess Club", "Ana", Duration.ofDays(1)).text()).contains("このリンクは1日間有効です。");
		}

		@Test
		void compose_escapesNamesInHtml() {
			OutgoingEmail email = composeJapanese("<script>alert(1)</script>", "<b onmouseover=x>Ana</b>", Duration.ofDays(7));

			assertThat(email.html()).doesNotContain("<script>alert(1)</script>").doesNotContain("<b onmouseover=x>");
			assertThat(email.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
		}

	}

}
