package io.grouploom.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.attendance.AttendanceRepository;
import io.grouploom.backend.attendance.GoingCount;
import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.dto.CancelEventRequest;
import io.grouploom.backend.event.dto.CreateEventRequest;
import io.grouploom.backend.event.dto.UpdateEventRequest;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.notification.EventNotificationRepository;
import io.grouploom.backend.notification.EventNotificationService;
import io.grouploom.backend.notification.NotificationStatus;
import io.grouploom.backend.user.Role;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Mock EventRepository repository;
	@Mock EventTypeRepository eventTypes;
	@Mock GroupRepository groups;
	@Mock EventNotificationService notificationService;
	@Mock EventNotificationRepository notificationRepository;
	@Mock AttendanceRepository attendances;

	private final UUID groupId = UUID.randomUUID();
	private final CurrentUser current = new CurrentUser(UUID.randomUUID(), groupId, Role.ORGANIZER);
	private Group group;
	private EventType practice;
	private EventService service;

	@BeforeEach
	void setUp() {
		group = new Group("Club", CREATED);
		ReflectionTestUtils.setField(group, "id", groupId);
		given(groups.getReferenceById(groupId)).willReturn(group);
		practice = eventType("Practice");
		given(repository.saveAndFlush(any(Event.class))).willAnswer(i -> i.getArgument(0));
		service = new EventService(repository, eventTypes, groups, notificationService, notificationRepository,
				attendances, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private EventType eventType(String name) {
		EventType t = new EventType(group, name, null, EventTypeColor.GREEN, null, CREATED);
		ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
		given(eventTypes.findByIdAndGroupId(t.getId(), groupId)).willReturn(Optional.of(t));
		return t;
	}

	private EventType archivedType(String name) {
		EventType t = eventType(name);
		t.archive(CREATED);
		return t;
	}

	private CreateEventRequest create(UUID typeId, String localStart, String localEnd, String zone) {
		return new CreateEventRequest(typeId, "  Practice  ", "desc", "Gym", LocalDateTime.parse(localStart),
				LocalDateTime.parse(localEnd), zone);
	}

	private CreateEventRequest create(String localStart, String localEnd) {
		return create(practice.getId(), localStart, localEnd, "UTC");
	}

	private Event existing(EventType type, String startUtc, String endUtc) {
		Event e = new Event(group, type, "Old", null, null, Instant.parse(startUtc), Instant.parse(endUtc), "UTC", CREATED);
		ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
		given(repository.findByIdAndGroupId(e.getId(), groupId)).willReturn(Optional.of(e));
		return e;
	}

	private UpdateEventRequest update(Event e, UUID typeId, String title, String localStart, String localEnd, long version) {
		return new UpdateEventRequest(typeId, title, null, null, LocalDateTime.parse(localStart), LocalDateTime.parse(localEnd),
				"UTC", version);
	}

	private void assertBadRequest(Runnable action, String code, String field) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BadRequestException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getField()).isEqualTo(field);
		});
	}

	// --- create ---

	@Test
	void create_withValidRequest_trimsTitleAndSavesInstantsWithZone() {
		var response = service.create(current, create("2026-06-02T10:00:00", "2026-06-02T11:30:00"));

		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(repository).saveAndFlush(captor.capture());
		Event saved = captor.getValue();
		assertThat(saved.getTitle()).isEqualTo("Practice");
		assertThat(saved.getGroup()).isSameAs(group);
		assertThat(saved.getEventType()).isSameAs(practice);
		assertThat(saved.getStartsAt()).isEqualTo(Instant.parse("2026-06-02T10:00:00Z"));
		assertThat(saved.getEndsAt()).isEqualTo(Instant.parse("2026-06-02T11:30:00Z"));
		assertThat(saved.getTimeZone()).isEqualTo("UTC");
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(response.eventType().name()).isEqualTo("Practice");
	}

	@Test
	void create_withWallClockInZone_convertsToTheCorrectInstants() {
		var response = service.create(current, create(practice.getId(), "2026-11-02T10:00:00", "2026-11-02T12:00:00", "Europe/Madrid"));

		// 2 November 2026 is winter time in Madrid: UTC+1.
		assertThat(response.start()).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
		assertThat(response.end()).isEqualTo(Instant.parse("2026-11-02T11:00:00Z"));
		assertThat(response.localStart()).isEqualTo(LocalDateTime.parse("2026-11-02T10:00:00"));
		assertThat(response.localEnd()).isEqualTo(LocalDateTime.parse("2026-11-02T12:00:00"));
		assertThat(response.timeZone()).isEqualTo("Europe/Madrid");
	}

	@Test
	void create_withStartInDstGap_shiftsForward() {
		// Madrid skips 02:00-03:00 on 28 March 2027; 02:30 does not exist and becomes 03:30 (UTC+2).
		var response = service.create(current, create(practice.getId(), "2027-03-27T23:00:00", "2027-03-28T04:00:00", "Europe/Madrid"));
		var gap = service.create(current, create(practice.getId(), "2027-03-28T02:30:00", "2027-03-28T04:30:00", "Europe/Madrid"));

		assertThat(response.start()).isEqualTo(Instant.parse("2027-03-27T22:00:00Z"));
		assertThat(gap.start()).isEqualTo(Instant.parse("2027-03-28T01:30:00Z"));
		assertThat(gap.localStart()).isEqualTo(LocalDateTime.parse("2027-03-28T03:30:00"));
	}

	@Test
	void create_withStartInDstOverlap_usesTheEarlierOffset() {
		// Madrid repeats 02:00-03:00 on 25 October 2026; the first 02:30 is UTC+2.
		var response = service.create(current, create(practice.getId(), "2026-10-25T02:30:00", "2026-10-25T04:00:00", "Europe/Madrid"));

		assertThat(response.start()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
	}

	@Test
	void create_whenGapShiftMakesEndNotAfterStart_throwsEndBeforeStart() {
		// 02:50 shifts to 03:50 while 03:20 stays: the wall-clock order flips once converted.
		assertBadRequest(() -> service.create(current,
				create(practice.getId(), "2027-03-28T02:50:00", "2027-03-28T03:20:00", "Europe/Madrid")),
				"event.end-before-start", "localEnd");
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void create_withStartInThePast_throwsStartInPast() {
		assertBadRequest(() -> service.create(current, create("2026-06-01T11:00:00", "2026-06-01T13:00:00")),
				"event.start-in-past", "localStart");
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void create_withStartWithinOneMinuteAgo_isAllowed() {
		service.create(current, create("2026-06-01T11:59:30", "2026-06-01T13:00:00"));

		verify(repository).saveAndFlush(any(Event.class));
	}

	@Test
	void create_withEventLongerThanSevenDays_throwsTooLong() {
		assertBadRequest(() -> service.create(current, create("2026-06-02T10:00:00", "2026-06-09T10:00:01")),
				"event.too-long", "localEnd");
	}

	@Test
	void create_withEventOfExactlySevenDays_isAllowed() {
		service.create(current, create("2026-06-02T10:00:00", "2026-06-09T10:00:00"));

		verify(repository).saveAndFlush(any(Event.class));
	}

	@Test
	void create_withArchivedType_throwsEventTypeArchived() {
		EventType archived = archivedType("Old");

		assertBadRequest(() -> service.create(current, create(archived.getId(), "2026-06-02T10:00:00", "2026-06-02T11:00:00", "UTC")),
				"event.event-type-archived", "eventTypeId");
	}

	@Test
	void create_withTypeFromAnotherGroupOrMissing_throwsEventTypeNotFound() {
		UUID foreign = UUID.randomUUID();
		given(eventTypes.findByIdAndGroupId(foreign, groupId)).willReturn(Optional.empty());

		assertBadRequest(() -> service.create(current, create(foreign, "2026-06-02T10:00:00", "2026-06-02T11:00:00", "UTC")),
				"event.event-type-not-found", "eventTypeId");
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void create_whenFlushViolatesTheTypeForeignKey_translatesToEventTypeNotFound() {
		given(repository.saveAndFlush(any(Event.class))).willThrow(new DataIntegrityViolationException("fk"));

		assertBadRequest(() -> service.create(current, create("2026-06-02T10:00:00", "2026-06-02T11:00:00")),
				"event.event-type-not-found", "eventTypeId");
	}

	// --- update ---

	@Test
	void update_withMatchingVersion_appliesChanges() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		EventType other = eventType("Board");

		var response = service.update(current, e.getId(),
				update(e, other.getId(), "New title", "2026-06-03T09:00:00", "2026-06-03T10:00:00", 0));

		assertThat(response.title()).isEqualTo("New title");
		assertThat(response.eventType().name()).isEqualTo("Board");
		assertThat(response.start()).isEqualTo(Instant.parse("2026-06-03T09:00:00Z"));
		assertThat(response.updatedAt()).isEqualTo(NOW);
		assertThat(response.createdAt()).isEqualTo(CREATED);
	}

	@Test
	void update_withStaleVersion_throwsStale() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		ReflectionTestUtils.setField(e, "version", 3L);

		assertThatThrownBy(() -> service.update(current, e.getId(),
				update(e, practice.getId(), "X", "2026-06-02T10:00:00", "2026-06-02T11:00:00", 2)))
			.isInstanceOfSatisfying(ConflictException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("event.stale");
				assertThat(ex.getField()).isNull();
			});
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void update_whenFlushDetectsConcurrentEdit_translatesToStale() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		given(repository.saveAndFlush(any(Event.class))).willThrow(new ObjectOptimisticLockingFailureException(Event.class, e.getId()));

		assertThatThrownBy(() -> service.update(current, e.getId(),
				update(e, practice.getId(), "X", "2026-06-02T10:00:00", "2026-06-02T11:00:00", 0)))
			.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("event.stale"));
	}

	@Test
	void update_ofPastEventKeepingStart_canChangeTheTitle() {
		Event e = existing(practice, "2026-05-01T10:00:00Z", "2026-05-01T11:00:00Z");

		var response = service.update(current, e.getId(),
				update(e, practice.getId(), "Corrected", "2026-05-01T10:00:00", "2026-05-01T11:30:00", 0));

		assertThat(response.title()).isEqualTo("Corrected");
		assertThat(response.end()).isEqualTo(Instant.parse("2026-05-01T11:30:00Z"));
	}

	@Test
	void update_movingTheStartIntoThePast_throwsStartInPast() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		assertBadRequest(() -> service.update(current, e.getId(),
				update(e, practice.getId(), "X", "2026-05-31T10:00:00", "2026-05-31T11:00:00", 0)),
				"event.start-in-past", "localStart");
	}

	@Test
	void update_withEventLongerThanSevenDays_throwsTooLong() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		assertBadRequest(() -> service.update(current, e.getId(),
				update(e, practice.getId(), "X", "2026-06-02T10:00:00", "2026-07-02T10:00:00", 0)),
				"event.too-long", "localEnd");
	}

	@Test
	void update_keepingAnArchivedType_isAllowed() {
		EventType archived = archivedType("Old");
		Event e = existing(archived, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		var response = service.update(current, e.getId(),
				update(e, archived.getId(), "Renamed", "2026-06-02T10:00:00", "2026-06-02T11:00:00", 0));

		assertThat(response.eventType().archived()).isTrue();
		assertThat(response.title()).isEqualTo("Renamed");
	}

	@Test
	void update_switchingToAnArchivedType_throwsEventTypeArchived() {
		EventType archived = archivedType("Old");
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		assertBadRequest(() -> service.update(current, e.getId(),
				update(e, archived.getId(), "X", "2026-06-02T10:00:00", "2026-06-02T11:00:00", 0)),
				"event.event-type-archived", "eventTypeId");
	}

	@Test
	void update_switchingToTypeFromAnotherGroup_throwsEventTypeNotFound() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		UUID foreign = UUID.randomUUID();
		given(eventTypes.findByIdAndGroupId(foreign, groupId)).willReturn(Optional.empty());

		assertBadRequest(() -> service.update(current, e.getId(),
				update(e, foreign, "X", "2026-06-02T10:00:00", "2026-06-02T11:00:00", 0)),
				"event.event-type-not-found", "eventTypeId");
	}

	// --- get / delete / tenancy ---

	@Test
	void get_withOwnEvent_returnsIt() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		assertThat(service.get(current, e.getId()).title()).isEqualTo("Old");
	}

	@Test
	void getUpdateDelete_withEventFromAnotherGroup_throwNotFound() {
		UUID foreignId = UUID.randomUUID();
		given(repository.findByIdAndGroupId(foreignId, groupId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(current, foreignId)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(current, foreignId,
				new UpdateEventRequest(practice.getId(), "X", null, null, LocalDateTime.parse("2026-06-02T10:00:00"),
						LocalDateTime.parse("2026-06-02T11:00:00"), "UTC", 0L)))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(current, foreignId)).isInstanceOf(NotFoundException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	void delete_withOwnEvent_deletesIt() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		service.delete(current, e.getId());

		verify(repository).delete(e);
	}

	// --- cancel ---

	@Test
	void cancel_withUpcomingEvent_marksItCancelledWithTheMessage() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		var response = service.cancel(current, e.getId(), new CancelEventRequest("Gym is closed"));

		assertThat(e.isCancelled()).isTrue();
		assertThat(response.cancelledAt()).isEqualTo(NOW);
		assertThat(response.cancellationMessage()).isEqualTo("Gym is closed");
	}

	@Test
	void cancel_withoutMessage_cancelsWithNoMessage() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		var response = service.cancel(current, e.getId(), new CancelEventRequest(null));

		assertThat(response.cancelledAt()).isEqualTo(NOW);
		assertThat(response.cancellationMessage()).isNull();
	}

	@Test
	void cancel_withBlankMessage_storesNoMessage() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		assertThat(service.cancel(current, e.getId(), new CancelEventRequest("   ")).cancellationMessage()).isNull();
	}

	@Test
	void cancel_whenAlreadyCancelled_throwsConflict() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		e.cancel(null, CREATED);

		assertConflict(() -> service.cancel(current, e.getId(), new CancelEventRequest(null)), "event.cancelled");
	}

	@Test
	void cancel_whenTheEventHasEnded_throwsConflict() {
		Event e = existing(practice, "2026-05-30T10:00:00Z", "2026-05-30T11:00:00Z");

		assertConflict(() -> service.cancel(current, e.getId(), new CancelEventRequest(null)), "event.ended");
		assertThat(e.isCancelled()).isFalse();
	}

	@Test
	void cancel_withEventFromAnotherGroup_throwsNotFound() {
		UUID foreignId = UUID.randomUUID();
		given(repository.findByIdAndGroupId(foreignId, groupId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.cancel(current, foreignId, new CancelEventRequest(null)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void update_ofACancelledEvent_throwsConflict() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		e.cancel(null, CREATED);

		assertConflict(() -> service.update(current, e.getId(),
				update(e, practice.getId(), "New", "2026-06-02T10:00:00", "2026-06-02T11:00:00", 0)), "event.cancelled");
	}

	private void assertConflict(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ConflictException.class,
				e -> assertThat(e.getCode()).isEqualTo(code));
	}

	@Test
	void cancel_tellsTheNotificationServiceAfterFlushing() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		service.cancel(current, e.getId(), new CancelEventRequest("Closed"));

		var order = org.mockito.Mockito.inOrder(repository, notificationService);
		order.verify(repository).saveAndFlush(e);
		order.verify(notificationService).onEventCancelled(e, current);
	}

	@Test
	void cancel_whenRejected_doesNotTouchNotifications() {
		Event e = existing(practice, "2026-05-30T10:00:00Z", "2026-05-30T11:00:00Z");

		assertThatThrownBy(() -> service.cancel(current, e.getId(), new CancelEventRequest(null)))
			.isInstanceOf(ConflictException.class);

		verify(notificationService, never()).onEventCancelled(any(), any());
	}

	@Test
	void update_whenTheStartMoves_tellsTheNotificationService() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		service.update(current, e.getId(), update(e, practice.getId(), "Old", "2026-06-03T10:00:00", "2026-06-03T11:00:00", 0));

		verify(notificationService).onEventRescheduled(e, Instant.parse("2026-06-03T10:00:00Z"));
	}

	@Test
	void update_whenTheStartIsUnchanged_doesNotTouchNotifications() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");

		service.update(current, e.getId(), update(e, practice.getId(), "Renamed", "2026-06-02T10:00:00", "2026-06-02T12:00:00", 0));

		verify(notificationService, never()).onEventRescheduled(any(), any());
	}

	@Test
	void delete_afterNotificationsWereSent_throwsHasNotifications() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		given(notificationRepository.existsByEventIdAndStatus(e.getId(), NotificationStatus.SENT)).willReturn(true);

		assertConflict(() -> service.delete(current, e.getId()), "event.has-notifications");
		verify(repository, never()).delete(any());
	}

	// --- list ---

	@Test
	void list_withUpcoming_queriesUpcomingForTheGroupWithTheClockNow() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		PageRequest page = PageRequest.of(0, 20);
		UUID typeId = practice.getId();
		given(repository.findUpcoming(groupId, NOW, typeId, page)).willReturn(new PageImpl<>(List.of(e), page, 1));

		assertThat(service.list(current, When.UPCOMING, typeId, page).getContent()).extracting("title").containsExactly("Old");
		verify(repository, never()).findPast(any(), any(), any(), any());
	}

	@Test
	void list_withPast_queriesPast() {
		PageRequest page = PageRequest.of(0, 20);
		given(repository.findPast(groupId, NOW, null, page)).willReturn(new PageImpl<>(List.of(), page, 0));

		assertThat(service.list(current, When.PAST, null, page).getContent()).isEmpty();
		verify(repository, never()).findUpcoming(any(), any(), any(), any());
	}

	// --- going counts ---

	@Test
	void list_fillsTheGoingCountsFromOneGroupedQuery() {
		Event first = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		Event second = existing(practice, "2026-06-03T10:00:00Z", "2026-06-03T11:00:00Z");
		PageRequest page = PageRequest.of(0, 20);
		given(repository.findUpcoming(groupId, NOW, null, page)).willReturn(new PageImpl<>(List.of(first, second), page, 2));
		given(attendances.countGoing(any())).willReturn(List.of(new GoingCount(first.getId(), 12)));

		var content = service.list(current, When.UPCOMING, null, page).getContent();

		assertThat(content).extracting("goingCount").containsExactly(12L, 0L);
		ArgumentCaptor<java.util.Collection<UUID>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(attendances, org.mockito.Mockito.times(1)).countGoing(ids.capture());
		assertThat(ids.getValue()).containsExactlyInAnyOrder(first.getId(), second.getId());
	}

	@Test
	void list_withAnEmptyPage_doesNotQueryGoingCounts() {
		PageRequest page = PageRequest.of(0, 20);
		given(repository.findPast(groupId, NOW, null, page)).willReturn(new PageImpl<>(List.of(), page, 0));

		service.list(current, When.PAST, null, page);

		verify(attendances, never()).countGoing(any());
	}

	@Test
	void get_fillsTheGoingCount() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		given(attendances.countGoing(List.of(e.getId()))).willReturn(List.of(new GoingCount(e.getId(), 5)));

		assertThat(service.get(current, e.getId()).goingCount()).isEqualTo(5);
	}

	@Test
	void update_returnsTheCurrentGoingCount() {
		Event e = existing(practice, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z");
		given(attendances.countGoing(List.of(e.getId()))).willReturn(List.of(new GoingCount(e.getId(), 4)));

		var response = service.update(current, e.getId(),
				update(e, practice.getId(), "New", "2026-06-02T10:00:00", "2026-06-02T11:00:00", e.getVersion()));

		assertThat(response.goingCount()).isEqualTo(4);
	}

	@Test
	void create_startsWithNoOneGoingAndDoesNotQuery() {
		var response = service.create(current, create("2026-06-02T10:00:00", "2026-06-02T11:30:00"));

		assertThat(response.goingCount()).isZero();
		verify(attendances, never()).countGoing(any());
	}

}
