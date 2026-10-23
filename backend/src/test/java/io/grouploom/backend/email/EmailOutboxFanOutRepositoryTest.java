package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.member.MemberRepository;
import io.grouploom.backend.notification.EventNotification;
import io.grouploom.backend.notification.EventNotificationRepository;
import io.grouploom.backend.notification.NotificationKind;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EmailOutboxFanOutRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired GroupRepository groups;
	@Autowired MemberRepository members;
	@Autowired EventTypeRepository eventTypes;
	@Autowired EventRepository events;
	@Autowired EventNotificationRepository notifications;
	@Autowired EmailOutboxRepository outbox;
	@Autowired JdbcTemplate jdbc;

	private Group group;
	private EventType practice;
	private EventType social;
	private Event event;
	private EventNotification invitation;
	private int counter;

	@BeforeEach
	void setUp() {
		group = groups.save(new Group("Club", NOW));
		practice = eventTypes.save(new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW));
		social = eventTypes.save(new EventType(group, "Social", null, EventTypeColor.BLUE, null, NOW));
		Instant start = NOW.plus(Duration.ofDays(5));
		event = events.saveAndFlush(new Event(group, practice, "Practice", null, null, start, start.plusSeconds(3600), "UTC", NOW));
		invitation = notifications.saveAndFlush(
				new EventNotification(event, group, NotificationKind.INVITATION, NOW, null, null, null, NOW));
	}

	private Member member(Group g) {
		return members.saveAndFlush(new Member(g, "Member " + counter, "m" + counter++ + "@example.com", NOW));
	}

	private Member member() {
		return member(group);
	}

	private void pause(Member m) {
		jdbc.update("insert into member_preferences (member_id, invitations_paused_at, updated_at) values (?, now(), now())",
				m.getId());
	}

	private void optOut(Member m, EventType type) {
		jdbc.update("insert into member_event_type_opt_outs (member_id, event_type_id) values (?, ?)", m.getId(), type.getId());
	}

	private int fanOutInvitation() {
		return outbox.fanOutInvitation(invitation.getId(), event.getId(), group.getId(), practice.getId(),
				"EVENT_INVITATION", null, NOW);
	}

	private List<UUID> recipients(String kind) {
		return jdbc.queryForList("select member_id from email_outbox where kind = ? and notification_id = ?", UUID.class,
				kind, invitation.getId());
	}

	@Test
	void fanOutInvitation_skipsPausedMembersMembersWhoOptedOutAndOtherGroups() {
		Member plain = member();
		Member pausedMember = member();
		pause(pausedMember);
		Member optedOut = member();
		optOut(optedOut, practice);
		Member optedOutOfOtherType = member();
		optOut(optedOutOfOtherType, social);
		Group other = groups.save(new Group("Other", NOW));
		member(other);

		int inserted = fanOutInvitation();

		assertThat(inserted).isEqualTo(2);
		assertThat(recipients("EVENT_INVITATION")).containsExactlyInAnyOrder(plain.getId(), optedOutOfOtherType.getId());
	}

	@Test
	void fanOutInvitation_includesMembersWhoUnpausedAndCreatesPendingRowsDueNow() {
		Member m = member();
		jdbc.update("insert into member_preferences (member_id, invitations_paused_at, updated_at) values (?, null, now())", m.getId());

		fanOutInvitation();

		assertThat(jdbc.queryForMap("select status, attempts, event_id, group_id, next_attempt_at from email_outbox"))
			.containsEntry("status", "PENDING")
			.containsEntry("attempts", 0)
			.containsEntry("event_id", event.getId())
			.containsEntry("group_id", group.getId());
	}

	@Test
	void fanOutInvitation_runTwice_insertsNothingTheSecondTime() {
		member();
		member();

		assertThat(fanOutInvitation()).isEqualTo(2);
		assertThat(fanOutInvitation()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from email_outbox", Long.class)).isEqualTo(2);
	}

	@Test
	void fanOutInvitation_recordsTheRequestingUser() {
		member();
		UUID userId = jdbc.queryForObject("insert into users (id, group_id, email, display_name, password_hash, role) "
				+ "values (gen_random_uuid(), ?, 'o@example.com', 'Olga', 'x', 'OWNER') returning id", UUID.class, group.getId());

		outbox.fanOutInvitation(invitation.getId(), event.getId(), group.getId(), practice.getId(), "EVENT_REMINDER",
				userId, NOW);

		assertThat(jdbc.queryForObject("select requested_by_user_id from email_outbox", UUID.class)).isEqualTo(userId);
		assertThat(jdbc.queryForObject("select kind from email_outbox", String.class)).isEqualTo("EVENT_REMINDER");
	}

	@Test
	void fanOutCancellation_targetsOnlyMembersWithSentInvitationsOrReminders() {
		Member invited = member();
		Member reminded = member();
		Member pendingOnly = member();
		Member failedOnly = member();
		Member neverEmailed = member();
		Member preferencesLinkOnly = member();
		insertOutbox(invited, "EVENT_INVITATION", "SENT", event);
		insertOutbox(reminded, "EVENT_REMINDER", "SENT", event);
		insertOutbox(reminded, "EVENT_INVITATION", "SENT", event);
		insertOutbox(pendingOnly, "EVENT_INVITATION", "PENDING", event);
		insertOutbox(failedOnly, "EVENT_INVITATION", "FAILED", event);
		insertOutbox(preferencesLinkOnly, "PREFERENCES_LINK", "SENT", null);
		Event otherEvent = events.saveAndFlush(new Event(group, practice, "Other", null, null, NOW.plus(Duration.ofDays(9)),
				NOW.plus(Duration.ofDays(9)).plusSeconds(60), "UTC", NOW));
		insertOutbox(neverEmailed, "EVENT_INVITATION", "SENT", otherEvent);
		pause(invited);
		EventNotification cancellation = notifications.saveAndFlush(
				new EventNotification(event, group, NotificationKind.CANCELLATION, NOW, null, null, null, NOW));

		int inserted = outbox.fanOutCancellation(cancellation.getId(), event.getId(), group.getId(), null, NOW);

		assertThat(inserted).isEqualTo(2);
		assertThat(jdbc.queryForList("select member_id from email_outbox where kind = 'EVENT_CANCELLATION'", UUID.class))
			.containsExactlyInAnyOrder(invited.getId(), reminded.getId());
		assertThat(outbox.fanOutCancellation(cancellation.getId(), event.getId(), group.getId(), null, NOW)).isZero();
	}

	@Test
	void countByNotification_returnsCountsGroupedByNotificationAndStatus() {
		Member a = member();
		Member b = member();
		Member c = member();
		EventNotification reminder = notifications.saveAndFlush(
				new EventNotification(event, group, NotificationKind.REMINDER, NOW, 60, null, null, NOW));
		insertOutbox(a, "EVENT_INVITATION", "SENT", event, invitation);
		insertOutbox(b, "EVENT_INVITATION", "SENT", event, invitation);
		insertOutbox(c, "EVENT_INVITATION", "FAILED", event, invitation);
		insertOutbox(a, "EVENT_REMINDER", "PENDING", event, reminder);

		List<DeliveryCount> counts = outbox.countByNotification(List.of(invitation.getId(), reminder.getId()));

		assertThat(counts).containsExactlyInAnyOrder(
				new DeliveryCount(invitation.getId(), EmailStatus.SENT, 2),
				new DeliveryCount(invitation.getId(), EmailStatus.FAILED, 1),
				new DeliveryCount(reminder.getId(), EmailStatus.PENDING, 1));
	}

	@Test
	void countEligibleRecipientsAndCountMembers_matchWhatTheFanOutWouldInsert() {
		member();
		pause(member());
		optOut(member(), practice);
		optOut(member(), social);
		member(groups.save(new Group("Other", NOW)));

		int eligible = outbox.countEligibleRecipients(group.getId(), practice.getId());

		assertThat(eligible).isEqualTo(2);
		assertThat(outbox.countMembers(group.getId())).isEqualTo(4);
		assertThat(fanOutInvitation()).isEqualTo(eligible);
	}

	@Test
	void insert_acceptsTheEventKinds() {
		Member m = member();
		for (String kind : List.of("EVENT_INVITATION", "EVENT_REMINDER", "EVENT_CANCELLATION")) {
			insertOutbox(m, kind, "PENDING", event);
		}

		assertThat(jdbc.queryForObject("select count(*) from email_outbox", Long.class)).isEqualTo(3);
	}

	@Test
	void insert_withTwoRowsForTheSameNotificationAndMember_violatesTheUniqueIndex() {
		Member m = member();
		insertOutbox(m, "EVENT_INVITATION", "PENDING", event, invitation);

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> insertOutbox(m, "EVENT_INVITATION", "PENDING", event, invitation))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deleteEvent_cascadesToItsOutboxRows() {
		insertOutbox(member(), "EVENT_INVITATION", "SENT", event, invitation);

		jdbc.update("delete from events where id = ?", event.getId());

		assertThat(jdbc.queryForObject("select count(*) from email_outbox", Long.class)).isZero();
	}

	@Test
	void findWithDetailsById_alsoLoadsEventTypeAndNotification() {
		Member m = member();
		insertOutbox(m, "EVENT_INVITATION", "PENDING", event, invitation);
		UUID id = jdbc.queryForObject("select id from email_outbox", UUID.class);
		jdbc.getDataSource();

		EmailOutboxEntry found = outbox.findWithDetailsById(id).orElseThrow();

		assertThat(found.getEvent().getEventType().getName()).isEqualTo("Practice");
		assertThat(found.getNotification().getId()).isEqualTo(invitation.getId());
	}

	private void insertOutbox(Member m, String kind, String status, Event e) {
		insertOutbox(m, kind, status, e, null);
	}

	private void insertOutbox(Member m, String kind, String status, Event e, EventNotification n) {
		jdbc.update("insert into email_outbox (id, group_id, member_id, kind, status, next_attempt_at, event_id, notification_id) "
				+ "values (gen_random_uuid(), ?, ?, ?, ?, now(), ?, ?)", m.getGroup().getId(), m.getId(), kind, status,
				e == null ? null : e.getId(), n == null ? null : n.getId());
	}

}
