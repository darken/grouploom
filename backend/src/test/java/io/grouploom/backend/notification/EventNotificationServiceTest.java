package io.grouploom.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.email.DeliveryCount;
import io.grouploom.backend.email.EmailOutboxRepository;
import io.grouploom.backend.email.EmailStatus;
import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.notification.dto.EventNotificationResponse;
import io.grouploom.backend.notification.dto.NotificationTiming;
import io.grouploom.backend.notification.dto.ScheduleNotificationRequest;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventNotificationServiceTest {

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant START = Instant.parse("2026-06-10T10:00:00Z");

	@Mock EventRepository events;
	@Mock EventNotificationRepository notifications;
	@Mock EmailOutboxRepository outbox;
	@Mock UserRepository users;
	@Mock GroupRepository groups;

	private final UUID groupId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final CurrentUser current = new CurrentUser(userId, groupId, Role.ORGANIZER);
	private Group group;
	private User user;
	private EventType type;
	private Event event;
	private EventNotificationService service;

	@BeforeEach
	void setUp() {
		group = new Group("Club", CREATED);
		ReflectionTestUtils.setField(group, "id", groupId);
		user = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, CREATED);
		type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, CREATED);
		ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
		event = event("UTC", START);
		given(groups.getReferenceById(groupId)).willReturn(group);
		given(users.getReferenceById(userId)).willReturn(user);
		given(notifications.save(any(EventNotification.class))).willAnswer(i -> i.getArgument(0));
		service = new EventNotificationService(events, notifications, outbox, users, groups, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Event event(String zone, Instant start) {
		Event e = new Event(group, type, "Practice", null, null, start, start.plus(Duration.ofHours(2)), zone, CREATED);
		ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
		given(events.findByIdAndGroupId(e.getId(), groupId)).willReturn(Optional.of(e));
		return e;
	}

	private EventNotification existing(NotificationKind kind, Instant sendAt, Integer offset) {
		EventNotification n = new EventNotification(event, group, kind, sendAt, offset, null, user, CREATED);
		ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
		given(notifications.findByIdAndEventIdAndGroupId(n.getId(), event.getId(), groupId)).willReturn(Optional.of(n));
		return n;
	}

	private ScheduleNotificationRequest now(NotificationKind kind) {
		return new ScheduleNotificationRequest(kind, NotificationTiming.NOW, null, null, null);
	}

	private ScheduleNotificationRequest before(int minutes) {
		return new ScheduleNotificationRequest(NotificationKind.REMINDER, NotificationTiming.BEFORE_START, minutes, null, null);
	}

	private ScheduleNotificationRequest at(String local) {
		return new ScheduleNotificationRequest(NotificationKind.REMINDER, NotificationTiming.AT, null,
				LocalDateTime.parse(local), null);
	}

	private void assertBadRequest(Runnable action, String code, String field) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BadRequestException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getField()).isEqualTo(field);
		});
	}

	private void assertConflict(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ConflictException.class,
				e -> assertThat(e.getCode()).isEqualTo(code));
	}

	private EventNotification saved() {
		ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
		verify(notifications).save(captor.capture());
		return captor.getValue();
	}

	// --- schedule ---

	@Test
	void schedule_now_isDueImmediately() {
		EventNotificationResponse response = service.schedule(current, event.getId(), now(NotificationKind.INVITATION));

		EventNotification n = saved();
		assertThat(n.getSendAt()).isEqualTo(NOW);
		assertThat(n.getOffsetMinutes()).isNull();
		assertThat(n.getKind()).isEqualTo(NotificationKind.INVITATION);
		assertThat(n.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
		assertThat(n.getCreatedBy()).isSameAs(user);
		assertThat(response.status()).isEqualTo(NotificationStatus.SCHEDULED);
		assertThat(response.delivery().pending()).isZero();
	}

	@Test
	void schedule_beforeStart_storesTheOffsetAndSubtractsItFromTheStart() {
		service.schedule(current, event.getId(), before(1440));

		EventNotification n = saved();
		assertThat(n.getOffsetMinutes()).isEqualTo(1440);
		assertThat(n.getSendAt()).isEqualTo(START.minus(Duration.ofDays(1)));
	}

	@Test
	void schedule_at_convertsWallClockInTheEventsZoneAcrossADstChange() {
		// Madrid leaves summer time on 25 October 2026: the 24th is UTC+2 and the 26th is UTC+1.
		Event madrid = event("Europe/Madrid", Instant.parse("2026-10-26T09:00:00Z"));

		service.schedule(current, madrid.getId(), at("2026-10-24T09:00:00"));
		service.schedule(current, madrid.getId(), at("2026-10-26T08:00:00"));

		ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
		verify(notifications, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues().get(0).getSendAt()).isEqualTo(Instant.parse("2026-10-24T07:00:00Z"));
		assertThat(captor.getAllValues().get(1).getSendAt()).isEqualTo(Instant.parse("2026-10-26T07:00:00Z"));
	}

	@Test
	void schedule_respondsWithLocalSendAtInTheEventsZone() {
		Event madrid = event("Europe/Madrid", Instant.parse("2026-10-26T09:00:00Z"));

		EventNotificationResponse response = service.schedule(current, madrid.getId(), at("2026-10-24T09:00:00"));

		assertThat(response.localSendAt()).isEqualTo(LocalDateTime.parse("2026-10-24T09:00:00"));
		assertThat(response.timeZone()).isEqualTo("Europe/Madrid");
	}

	@Test
	void schedule_beforeStartThatLandsInThePast_throwsSendAtInvalidOnOffset() {
		Event soon = event("UTC", NOW.plus(Duration.ofHours(2)));

		assertBadRequest(() -> service.schedule(current, soon.getId(), before(1440)), "notification.send-at-invalid",
				"offsetMinutes");
		verify(notifications, never()).save(any());
	}

	@Test
	void schedule_beforeStartWithinTheOneMinuteAllowance_isAllowed() {
		Event soon = event("UTC", NOW.plus(Duration.ofMinutes(15)).minusSeconds(30));

		service.schedule(current, soon.getId(), before(15));

		assertThat(saved().getSendAt()).isEqualTo(NOW.minusSeconds(30));
	}

	@Test
	void schedule_atInThePast_throwsSendAtInvalidOnLocalSendAt() {
		assertBadRequest(() -> service.schedule(current, event.getId(), at("2026-06-01T11:00:00")),
				"notification.send-at-invalid", "localSendAt");
	}

	@Test
	void schedule_atOrAfterTheStart_throwsSendAtInvalidOnLocalSendAt() {
		assertBadRequest(() -> service.schedule(current, event.getId(), at("2026-06-10T10:00:00")),
				"notification.send-at-invalid", "localSendAt");
		assertBadRequest(() -> service.schedule(current, event.getId(), at("2026-06-11T10:00:00")),
				"notification.send-at-invalid", "localSendAt");
	}

	@Test
	void schedule_whenTenAreAlreadyScheduled_throwsLimit() {
		given(notifications.countByEventIdAndStatus(event.getId(), NotificationStatus.SCHEDULED)).willReturn(10L);

		assertConflict(() -> service.schedule(current, event.getId(), now(NotificationKind.INVITATION)), "notification.limit");
	}

	@Test
	void schedule_whenNineAreScheduled_isAllowed() {
		given(notifications.countByEventIdAndStatus(event.getId(), NotificationStatus.SCHEDULED)).willReturn(9L);

		service.schedule(current, event.getId(), now(NotificationKind.INVITATION));

		verify(notifications).save(any());
	}

	@Test
	void schedule_forACancelledEvent_throwsEventCancelled() {
		event.cancel(null, CREATED);

		assertConflict(() -> service.schedule(current, event.getId(), now(NotificationKind.INVITATION)), "event.cancelled");
	}

	@Test
	void schedule_forAnEventThatHasStarted_throwsEventStarted() {
		Event started = event("UTC", NOW.minusSeconds(60));

		assertConflict(() -> service.schedule(current, started.getId(), now(NotificationKind.INVITATION)), "event.started");
	}

	@Test
	void schedule_forAnotherGroupsEvent_throwsNotFound() {
		UUID foreign = UUID.randomUUID();
		given(events.findByIdAndGroupId(foreign, groupId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.schedule(current, foreign, now(NotificationKind.INVITATION)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void schedule_withBlankMessage_storesNoMessageAndTrimsOtherwise() {
		service.schedule(current, event.getId(),
				new ScheduleNotificationRequest(NotificationKind.INVITATION, NotificationTiming.NOW, null, null, "  "));
		service.schedule(current, event.getId(),
				new ScheduleNotificationRequest(NotificationKind.INVITATION, NotificationTiming.NOW, null, null, " Bring water "));

		ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
		verify(notifications, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues().get(0).getMessage()).isNull();
		assertThat(captor.getAllValues().get(1).getMessage()).isEqualTo("Bring water");
	}

	@Test
	void schedule_aCancellationKind_throwsBadRequestOnKind() {
		assertBadRequest(() -> service.schedule(current, event.getId(), now(NotificationKind.CANCELLATION)),
				"notification.invalid-kind", "kind");
	}

	// --- unschedule ---

	@Test
	void unschedule_aScheduledNotification_deletesIt() {
		EventNotification n = existing(NotificationKind.REMINDER, START.minusSeconds(3600), 60);

		service.unschedule(current, event.getId(), n.getId());

		verify(notifications).delete(n);
	}

	@Test
	void unschedule_aSentNotification_throwsAlreadySent() {
		EventNotification n = existing(NotificationKind.INVITATION, NOW, null);
		n.markFannedOut(3, NOW);

		assertConflict(() -> service.unschedule(current, event.getId(), n.getId()), "notification.already-sent");
		verify(notifications, never()).delete(any());
	}

	@Test
	void unschedule_whenItWasClaimedInTheMeantime_throwsAlreadySent() {
		EventNotification n = existing(NotificationKind.INVITATION, NOW, null);
		org.mockito.Mockito.doThrow(new ObjectOptimisticLockingFailureException(EventNotification.class, n.getId()))
			.when(notifications).flush();

		assertConflict(() -> service.unschedule(current, event.getId(), n.getId()), "notification.already-sent");
	}

	@Test
	void unschedule_aMissingOrForeignNotification_throwsNotFound() {
		assertThatThrownBy(() -> service.unschedule(current, event.getId(), UUID.randomUUID()))
			.isInstanceOf(NotFoundException.class);
	}

	// --- list / audience ---

	@Test
	void list_returnsNotificationsWithDeliveryCountsFromOneGroupedQuery() {
		EventNotification a = existing(NotificationKind.INVITATION, NOW, null);
		a.markFannedOut(4, NOW);
		EventNotification b = existing(NotificationKind.REMINDER, START.minusSeconds(3600), 60);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(a, b));
		given(outbox.countByNotification(List.of(a.getId(), b.getId()))).willReturn(List.of(
				new DeliveryCount(a.getId(), EmailStatus.SENT, 2), new DeliveryCount(a.getId(), EmailStatus.PENDING, 1),
				new DeliveryCount(a.getId(), EmailStatus.SENDING, 1), new DeliveryCount(a.getId(), EmailStatus.FAILED, 1)));

		List<EventNotificationResponse> list = service.list(current, event.getId());

		assertThat(list).hasSize(2);
		assertThat(list.get(0).delivery().sent()).isEqualTo(2);
		assertThat(list.get(0).delivery().pending()).isEqualTo(2);
		assertThat(list.get(0).delivery().failed()).isEqualTo(1);
		assertThat(list.get(0).recipientCount()).isEqualTo(4);
		assertThat(list.get(1).delivery().sent()).isZero();
	}

	@Test
	void list_withNoNotifications_skipsTheCountQuery() {
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of());

		assertThat(service.list(current, event.getId())).isEmpty();
		verify(outbox, never()).countByNotification(any());
	}

	@Test
	void list_forAnotherGroupsEvent_throwsNotFound() {
		assertThatThrownBy(() -> service.list(current, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void audience_forAnInvitation_returnsEligibleAndTotalMembers() {
		given(outbox.countEligibleRecipients(groupId, type.getId(), event.getId(), "EVENT_INVITATION")).willReturn(18);
		given(outbox.countMembers(groupId)).willReturn(24L);

		var audience = service.audience(current, event.getId(), NotificationKind.INVITATION);

		assertThat(audience.eligible()).isEqualTo(18);
		assertThat(audience.total()).isEqualTo(24);
	}

	@Test
	void audience_forAReminder_countsWithoutMembersWhoCannotGo() {
		given(outbox.countEligibleRecipients(groupId, type.getId(), event.getId(), "EVENT_REMINDER")).willReturn(15);
		given(outbox.countMembers(groupId)).willReturn(24L);

		var audience = service.audience(current, event.getId(), NotificationKind.REMINDER);

		assertThat(audience.eligible()).isEqualTo(15);
		assertThat(audience.total()).isEqualTo(24);
	}

	@Test
	void audience_forACancellation_throwsInvalidKind() {
		assertThatThrownBy(() -> service.audience(current, event.getId(), NotificationKind.CANCELLATION))
			.isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("notification.invalid-kind"));
	}

	@Test
	void audience_forAnotherGroupsEvent_throwsNotFound() {
		assertThatThrownBy(() -> service.audience(current, UUID.randomUUID(), NotificationKind.INVITATION)).isInstanceOf(NotFoundException.class);
	}

	// --- onEventRescheduled ---

	@Test
	void onEventRescheduled_movesOffsetNotificationsWithTheEvent() {
		EventNotification n = existing(NotificationKind.REMINDER, START.minus(Duration.ofDays(1)), 1440);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(n));
		Instant newStart = START.plus(Duration.ofDays(1));

		service.onEventRescheduled(event, newStart);

		assertThat(n.getSendAt()).isEqualTo(newStart.minus(Duration.ofDays(1)));
	}

	@Test
	void onEventRescheduled_whenTheNewSendTimeIsPast_makesItDueNow() {
		EventNotification n = existing(NotificationKind.REMINDER, START.minus(Duration.ofDays(1)), 1440);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(n));

		service.onEventRescheduled(event, NOW.plus(Duration.ofHours(3)));

		assertThat(n.getSendAt()).isEqualTo(NOW);
	}

	@Test
	void onEventRescheduled_leavesAbsoluteNotificationsAloneWhileTheyStayBeforeTheNewStart() {
		Instant at = START.minus(Duration.ofHours(5));
		EventNotification n = existing(NotificationKind.REMINDER, at, null);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(n));

		service.onEventRescheduled(event, START.plus(Duration.ofDays(1)));

		assertThat(n.getSendAt()).isEqualTo(at);
	}

	@Test
	void onEventRescheduled_withAnAbsoluteNotificationAtOrAfterTheNewStart_throwsOnLocalStart() {
		EventNotification n = existing(NotificationKind.REMINDER, START.minus(Duration.ofHours(5)), null);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(n));

		assertBadRequest(() -> service.onEventRescheduled(event, START.minus(Duration.ofHours(5))),
				"event.notification-after-start", "localStart");
	}

	@Test
	void onEventRescheduled_ignoresNotificationsThatAreNoLongerScheduled() {
		EventNotification sent = existing(NotificationKind.INVITATION, NOW, null);
		sent.markFannedOut(1, NOW);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(sent));

		service.onEventRescheduled(event, NOW.plusSeconds(60));

		assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	// --- onEventCancelled ---

	@Test
	void onEventCancelled_cancelsScheduledNotifications() {
		EventNotification a = existing(NotificationKind.REMINDER, START.minusSeconds(60), 15);
		EventNotification b = existing(NotificationKind.REMINDER, START.minusSeconds(120), null);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(a, b));

		service.onEventCancelled(event, current);

		assertThat(a.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
		assertThat(b.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
		verify(notifications, never()).save(any());
	}

	@Test
	void onEventCancelled_whenSomethingWasSent_createsACancellationDueNowWithTheEventsMessage() {
		EventNotification sent = existing(NotificationKind.INVITATION, NOW.minusSeconds(60), null);
		sent.markFannedOut(5, NOW);
		given(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).willReturn(List.of(sent));
		given(notifications.existsByEventIdAndStatus(event.getId(), NotificationStatus.SENT)).willReturn(true);
		event.cancel("Gym is closed", NOW);

		service.onEventCancelled(event, current);

		EventNotification cancellation = saved();
		assertThat(cancellation.getKind()).isEqualTo(NotificationKind.CANCELLATION);
		assertThat(cancellation.getSendAt()).isEqualTo(NOW);
		assertThat(cancellation.getMessage()).isEqualTo("Gym is closed");
		assertThat(cancellation.getCreatedBy()).isSameAs(user);
		assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

}
