package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import io.grouploom.backend.attendance.Attendance;
import io.grouploom.backend.attendance.AttendanceRepository;
import io.grouploom.backend.attendance.AttendanceResponse;
import io.grouploom.backend.config.MailTemplateConfig;
import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.IssuedToken;
import io.grouploom.backend.notification.EventNotification;
import io.grouploom.backend.notification.NotificationKind;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;

class EventEmailComposersTest {

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	// 10:00 – 12:00 in Madrid (UTC+1) on Saturday 7 November 2026.
	private static final Instant START = Instant.parse("2026-11-07T09:00:00Z");
	private static final Instant END = Instant.parse("2026-11-07T11:00:00Z");
	private static final IssuedToken PREFERENCES = new IssuedToken("pref_tok-1", CREATED.plus(Duration.ofDays(60)));
	private static final IssuedToken UNSUBSCRIBE = new IssuedToken("unsub_tok-2", CREATED.plus(Duration.ofDays(60)));
	private static final IssuedToken ATTENDANCE = new IssuedToken("att_tok-3", CREATED.plus(Duration.ofDays(60)));

	private final TemplateEngine engine = new MailTemplateConfig().mailTemplateEngine();
	private final MemberTokenService tokens = mock(MemberTokenService.class);
	private final AttendanceRepository attendances = mock(AttendanceRepository.class);
	private final EventInvitationEmailComposer invitations = new EventInvitationEmailComposer(engine,
			TestProperties.app("https://app.example.com/", Duration.ofDays(60)), tokens, attendances);
	private final EventCancellationEmailComposer cancellations = new EventCancellationEmailComposer(engine,
			TestProperties.app("https://app.example.com/", Duration.ofDays(60)), tokens, attendances);

	private Group group;
	private Member member;
	private User organizer;
	private EventType type;

	@BeforeEach
	void setUp() {
		group = new Group("Chess Club", CREATED);
		member = new Member(group, "Ana", "ana@example.com", CREATED);
		organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, CREATED);
		type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, CREATED);
		given(tokens.issue(member, MemberTokenPurpose.PREFERENCES)).willReturn(PREFERENCES);
		given(tokens.issue(member, MemberTokenPurpose.UNSUBSCRIBE)).willReturn(UNSUBSCRIBE);
		given(tokens.issue(eq(member), eq(MemberTokenPurpose.ATTENDANCE), any(Event.class))).willReturn(ATTENDANCE);
	}

	private Event event(String title, String description, String location) {
		Event e = new Event(group, type, title, description, location, START, END, "Europe/Madrid", CREATED);
		ReflectionTestUtils.setField(e, "id", java.util.UUID.randomUUID());
		return e;
	}

	private EmailOutboxEntry entry(EmailKind kind, Event event, NotificationKind notificationKind, Instant sendAt,
			String note, User requestedBy) {
		EventNotification n = new EventNotification(event, group, notificationKind, sendAt, null, note, requestedBy, CREATED);
		return new EmailOutboxEntry(group, member, requestedBy, kind, event, n, CREATED);
	}

	private OutgoingEmail invitation(Event event, String note) {
		return invitations.compose(entry(EmailKind.EVENT_INVITATION, event, NotificationKind.INVITATION,
				Instant.parse("2026-11-01T08:00:00Z"), note, organizer));
	}

	private OutgoingEmail reminder(Event event, Instant sendAt) {
		return invitations.compose(entry(EmailKind.EVENT_REMINDER, event, NotificationKind.REMINDER, sendAt, null, organizer));
	}

	private OutgoingEmail cancellation(Event event, String note) {
		return cancellations.compose(entry(EmailKind.EVENT_CANCELLATION, event, NotificationKind.CANCELLATION,
				Instant.parse("2026-11-01T08:00:00Z"), note, organizer));
	}

	@Test
	void kinds_areDeclaredPerComposer() {
		assertThat(invitations.kinds()).containsExactlyInAnyOrder(EmailKind.EVENT_INVITATION, EmailKind.EVENT_REMINDER);
		assertThat(cancellations.kinds()).containsExactly(EmailKind.EVENT_CANCELLATION);
	}

	@Test
	void compose_invitation_hasTheDateAndTimeInTheSubject() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.subject()).isEqualTo("Practice · Sat, Nov 7, 10:00");
		assertThat(email.to()).isEqualTo("ana@example.com");
		assertThat(email.fromName()).isEqualTo("Chess Club via GroupLoom");
		assertThat(email.replyTo()).isEqualTo("olga@example.com");
	}

	@Test
	void compose_reminderTheDayBefore_saysTomorrow() {
		OutgoingEmail email = reminder(event("Practice", null, null), Instant.parse("2026-11-06T09:00:00Z"));

		assertThat(email.subject()).isEqualTo("Reminder: Practice is tomorrow at 10:00");
	}

	@Test
	void compose_reminderTheSameDay_saysToday() {
		assertThat(reminder(event("Practice", null, null), Instant.parse("2026-11-07T06:00:00Z")).subject())
			.isEqualTo("Reminder: Practice is today at 10:00");
	}

	@Test
	void compose_reminderFurtherAhead_namesTheDay() {
		assertThat(reminder(event("Practice", null, null), Instant.parse("2026-11-01T06:00:00Z")).subject())
			.isEqualTo("Reminder: Practice is on Sat, Nov 7 at 10:00");
	}

	@Test
	void compose_cancellation_hasTheDateInTheSubject() {
		assertThat(cancellation(event("Practice", null, null), null).subject()).isEqualTo("Cancelled: Practice on Sat, Nov 7");
	}

	@Test
	void compose_includesTheEventDetailsInBothParts() {
		Event e = event("Practice", "Bring a board", "Main hall");

		for (OutgoingEmail email : new OutgoingEmail[] { invitation(e, "See you there"), reminder(e, Instant.parse("2026-11-06T09:00:00Z")) }) {
			for (String body : new String[] { email.text(), email.html() }) {
				assertThat(body).contains("Practice").contains("Sat, Nov 7 · 10:00 – 12:00 CET").contains("Main hall")
					.contains("Bring a board");
			}
			assertThat(email.html()).contains("#34c759");
		}
		assertThat(invitation(e, "See you there").text()).contains("See you there");
		assertThat(invitation(e, "See you there").html()).contains("See you there");
	}

	@Test
	void compose_withoutLocationDescriptionOrNote_omitsThoseLines() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.text()).doesNotContain("null");
		assertThat(email.html()).doesNotContain("null").doesNotContain("Location");
	}

	@Test
	void compose_cancellation_saysItIsCancelledAndShowsTheNote() {
		OutgoingEmail email = cancellation(event("Practice", null, "Main hall"), "The gym is closed");

		for (String body : new String[] { email.text(), email.html() }) {
			assertThat(body).containsIgnoringCase("cancelled").contains("The gym is closed").contains("Sat, Nov 7 · 10:00 – 12:00 CET");
		}
	}

	@Test
	void compose_setsTheOneClickUnsubscribeHeaders() {
		for (OutgoingEmail email : new OutgoingEmail[] { invitation(event("Practice", null, null), null),
				reminder(event("Practice", null, null), Instant.parse("2026-11-06T09:00:00Z")),
				cancellation(event("Practice", null, null), null) }) {
			assertThat(email.headers()).containsEntry("List-Unsubscribe",
					"<https://app.example.com/api/public/unsubscribe?t=unsub_tok-2>")
				.containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
		}
	}

	@Test
	void compose_footerLinksToPreferencesInTheFragmentAndNeverShowsTheUnsubscribeToken() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		for (String body : new String[] { email.text(), email.html() }) {
			assertThat(body).contains("https://app.example.com/m/preferences#t=pref_tok-1");
			assertThat(body).contains("you're a member of Chess Club");
			assertThat(body).doesNotContain("unsub_tok-2");
		}
		assertThat(email.text()).doesNotContain("<");
	}

	@Test
	void compose_htmlOffersLightAndDarkColorSchemesAndHasNoImages() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.html()).contains("<meta name=\"color-scheme\" content=\"light dark\"")
			.contains("prefers-color-scheme: dark")
			.doesNotContain("<img");
	}

	@Test
	void compose_escapesUserContentInHtml() {
		Event e = event("<script>alert(1)</script>", "<i onclick=x>desc</i>", "<b>Hall</b>");

		for (OutgoingEmail email : new OutgoingEmail[] { invitation(e, "<img src=x onerror=y>"), cancellation(e, "<img src=x onerror=y>") }) {
			assertThat(email.html()).doesNotContain("<script>alert(1)</script>").doesNotContain("<img src=x")
				.doesNotContain("<i onclick").doesNotContain("<b>Hall</b>");
			assertThat(email.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;").contains("&lt;img src=x onerror=y&gt;")
				.contains("&lt;b&gt;Hall&lt;/b&gt;");
		}
	}

	@Test
	void compose_stripsLineBreaksFromHeaderValues() {
		Event e = event("Practice\r\nBcc: evil@example.com", null, null);

		for (OutgoingEmail email : new OutgoingEmail[] { invitation(e, null), cancellation(e, null) }) {
			assertThat(email.subject()).doesNotContain("\r").doesNotContain("\n");
		}
	}

	@Test
	void compose_withoutRequester_hasNoReplyTo() {
		Event e = event("Practice", null, null);
		OutgoingEmail email = invitations.compose(entry(EmailKind.EVENT_INVITATION, e, NotificationKind.INVITATION, CREATED, null, null));

		assertThat(email.replyTo()).isNull();
	}

	// --- attendance ---

	private static final String GOING_LINK = "https://app.example.com/m/attend#t=att_tok-3&r=going";
	private static final String NOT_GOING_LINK = "https://app.example.com/m/attend#t=att_tok-3&r=not-going";
	private static final String CHANGE_LINK = "https://app.example.com/m/attend#t=att_tok-3";

	// Attribute values in the HTML part escape the ampersand.
	private static String inHtml(String link) {
		return link.replace("&", "&amp;");
	}

	private void answered(Event event, AttendanceResponse response) {
		Attendance a = BeanUtils.instantiateClass(Attendance.class);
		ReflectionTestUtils.setField(a, "response", response);
		given(attendances.findByEventIdAndMemberId(eq(event.getId()), any())).willReturn(Optional.of(a));
	}

	@Test
	void compose_invitationWithoutAnAnswer_hasBothReplyLinksInHtmlAndText() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.html()).contains("href=\"" + inHtml(GOING_LINK) + "\"").contains("href=\"" + inHtml(NOT_GOING_LINK) + "\"")
			.contains(">Going<").contains(">Can't go<").contains("You can change your answer until the event starts.");
		assertThat(email.text()).contains(GOING_LINK).contains(NOT_GOING_LINK)
			.contains("You can change your answer until the event starts.");
		assertThat(email.html()).doesNotContain("Change answer").doesNotContain("You're going");
		assertThat(email.text()).doesNotContain("Change answer");
	}

	@Test
	void compose_invitationWithoutAnAnswer_showsTheReplyBlockOnceAndInsideTheCard() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.html().split(">Going<", -1)).hasSize(2);
		assertThat(email.html().split("You can change your answer until the event starts.", -1)).hasSize(2);
		assertThat(email.html().indexOf(">Going<")).isLessThan(email.html().indexOf("You're getting this because"));
	}

	@Test
	void compose_reminderForAMemberWhoIsGoing_showsTheStatusOnce() {
		Event e = event("Practice", null, null);
		answered(e, AttendanceResponse.GOING);

		OutgoingEmail email = reminder(e, Instant.parse("2026-11-06T09:00:00Z"));

		assertThat(email.html().split("You're going", -1)).hasSize(2);
		assertThat(email.html().split("Change answer", -1)).hasSize(2);
		assertThat(email.html().indexOf("You're going")).isLessThan(email.html().indexOf("You're getting this because"));
	}

	@Test
	void compose_reminderWithoutAnAnswer_hasBothReplyLinksInHtmlAndText() {
		OutgoingEmail email = reminder(event("Practice", null, null), Instant.parse("2026-11-06T09:00:00Z"));

		assertThat(email.html()).contains(inHtml(GOING_LINK)).contains(inHtml(NOT_GOING_LINK));
		assertThat(email.text()).contains(GOING_LINK).contains(NOT_GOING_LINK);
	}

	@Test
	void compose_reminderForAMemberWhoIsGoing_showsTheStatusAndAChangeLinkWithoutAChoice() {
		Event e = event("Practice", null, null);
		answered(e, AttendanceResponse.GOING);

		OutgoingEmail email = reminder(e, Instant.parse("2026-11-06T09:00:00Z"));

		assertThat(email.html()).contains("You're going").contains("Change answer").contains("href=\"" + CHANGE_LINK + "\"")
			.doesNotContain("r=going").doesNotContain("r=not-going").doesNotContain(">Can't go<");
		assertThat(email.text()).contains("You're going").contains("Change answer").contains(CHANGE_LINK)
			.doesNotContain("r=going").doesNotContain("r=not-going");
	}

	@Test
	void compose_invitationForAMemberWhoCannotGo_showsTheStatusAndAChangeLink() {
		Event e = event("Practice", null, null);
		answered(e, AttendanceResponse.NOT_GOING);

		OutgoingEmail email = invitation(e, null);

		assertThat(email.html()).contains("You said you can't go").contains("Change answer").contains(CHANGE_LINK);
		assertThat(email.text()).contains("You said you can't go").contains(CHANGE_LINK);
		assertThat(email.html()).doesNotContain("r=going").doesNotContain("You can change your answer until");
	}

	@Test
	void compose_cancellation_hasNoAttendanceBlockAndIssuesNoAttendanceToken() {
		OutgoingEmail email = cancellation(event("Practice", null, null), null);

		for (String body : new String[] { email.text(), email.html() }) {
			assertThat(body).doesNotContain("/m/attend").doesNotContain("att_tok-3").doesNotContain("Change answer");
		}
		verify(tokens, never()).issue(any(), eq(MemberTokenPurpose.ATTENDANCE), any());
	}

	@Test
	void compose_attendanceLinksCarryAFreshTokenForEachEmail() {
		Event e = event("Practice", null, null);
		given(tokens.issue(eq(member), eq(MemberTokenPurpose.ATTENDANCE), eq(e)))
			.willReturn(new IssuedToken("first-tok", CREATED), new IssuedToken("second-tok", CREATED));

		OutgoingEmail first = invitation(e, null);
		OutgoingEmail second = invitation(e, null);

		assertThat(first.text()).contains("#t=first-tok&r=going").doesNotContain("second-tok");
		assertThat(second.text()).contains("#t=second-tok&r=going").doesNotContain("first-tok");
	}

	@Test
	void compose_attendanceToken_isBoundToTheEventOfTheEmail() {
		Event e = event("Practice", null, null);

		invitation(e, null);

		verify(tokens).issue(member, MemberTokenPurpose.ATTENDANCE, e);
	}

	@Test
	void compose_replyLinksNeverShowThePreferencesOrUnsubscribeTokens() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.text()).doesNotContain("unsub_tok-2");
		assertThat(email.html()).doesNotContain("unsub_tok-2");
	}

	@Test
	void compose_replyButtonsAdaptToDarkMode() {
		OutgoingEmail email = invitation(event("Practice", null, null), null);

		assertThat(email.html()).contains("btn-fill").contains("btn-outline");
	}

}
