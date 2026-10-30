package io.grouploom.backend.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.attendance.dto.AttendanceRowResponse;
import io.grouploom.backend.attendance.dto.AttendanceSummaryResponse;
import io.grouploom.backend.attendance.dto.SetAttendanceRequest;
import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.member.MemberRepository;
import io.grouploom.backend.user.Role;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
	private static final Instant STARTED = Instant.parse("2026-11-07T09:00:00Z");

	@Mock EventRepository events;
	@Mock MemberRepository members;
	@Mock AttendanceRepository attendances;

	private AttendanceService service;
	private CurrentUser current;
	private Event event;
	private Member member;

	@BeforeEach
	void setUp() {
		service = new AttendanceService(events, members, attendances, Clock.fixed(NOW, ZoneOffset.UTC));
		Group group = new Group("Chess Club", NOW);
		current = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), Role.ORGANIZER);
		ReflectionTestUtils.setField(group, "id", current.groupId());
		EventType type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW);
		// Already started: organizers can still correct records.
		event = new Event(group, type, "Practice", null, null, STARTED, STARTED.plusSeconds(3600), "UTC", STARTED);
		ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
		member = new Member(group, "Ana", "ana@example.com", NOW);
		ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
	}

	private void foundEvent() {
		given(events.findByIdAndGroupId(event.getId(), current.groupId())).willReturn(Optional.of(event));
	}

	private void foundMember() {
		given(members.findByIdAndGroupId(member.getId(), current.groupId())).willReturn(Optional.of(member));
	}

	@Test
	void summary_passesTheGroupThroughAndDerivesNoReply() {
		foundEvent();
		given(attendances.summarize(event.getId(), current.groupId())).willReturn(new AttendanceSummary() {
			public long getGoing() { return 12; }
			public long getNotGoing() { return 3; }
			public long getMembers() { return 24; }
		});

		AttendanceSummaryResponse summary = service.summary(current, event.getId());

		assertThat(summary).isEqualTo(new AttendanceSummaryResponse(12, 3, 9, 24));
	}

	@Test
	void summary_forAnotherGroupsEvent_throwsNotFound() {
		given(events.findByIdAndGroupId(event.getId(), current.groupId())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.summary(current, event.getId())).isInstanceOf(NotFoundException.class);
		verify(attendances, never()).summarize(any(), any());
	}

	@Test
	void list_passesTheGroupAndFilterThroughAndMapsRows() {
		foundEvent();
		Instant at = NOW.minusSeconds(60);
		AttendanceRow answered = new AttendanceRow(member.getId(), "Ana", "ana@example.com", AttendanceResponse.GOING, "Hi",
				AttendanceSource.ORGANIZER, at);
		AttendanceRow silent = new AttendanceRow(UUID.randomUUID(), "Bob", "bob@example.com", null, null, null, null);
		PageRequest pageable = PageRequest.of(0, 50);
		given(attendances.findRows(event.getId(), current.groupId(), AttendanceFilter.ALL, pageable))
			.willReturn(new PageImpl<>(List.of(answered, silent), pageable, 2));

		Page<AttendanceRowResponse> page = service.list(current, event.getId(), AttendanceFilter.ALL, pageable);

		assertThat(page.getContent()).containsExactly(
				new AttendanceRowResponse(new AttendanceRowResponse.MemberSummary(member.getId(), "Ana", "ana@example.com"),
						AttendanceResponse.GOING, "Hi", AttendanceSource.ORGANIZER, at),
				new AttendanceRowResponse(new AttendanceRowResponse.MemberSummary(silent.memberId(), "Bob", "bob@example.com"),
						null, null, null, null));
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void list_forAnotherGroupsEvent_throwsNotFound() {
		given(events.findByIdAndGroupId(event.getId(), current.groupId())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.list(current, event.getId(), AttendanceFilter.GOING, PageRequest.of(0, 50)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void set_upsertsWithSourceOrganizerAndTheUserId_evenAfterTheEventStarted() {
		foundEvent();
		foundMember();

		AttendanceRowResponse row = service.set(current, event.getId(), member.getId(),
				new SetAttendanceRequest(AttendanceResponse.NOT_GOING, "  Sick \n"));

		verify(attendances).upsert(current.groupId(), event.getId(), member.getId(), "NOT_GOING", "Sick", "ORGANIZER",
				current.userId(), NOW);
		assertThat(row.member().id()).isEqualTo(member.getId());
		assertThat(row.member().name()).isEqualTo("Ana");
		assertThat(row.response()).isEqualTo(AttendanceResponse.NOT_GOING);
		assertThat(row.note()).isEqualTo("Sick");
		assertThat(row.source()).isEqualTo(AttendanceSource.ORGANIZER);
		assertThat(row.respondedAt()).isEqualTo(NOW);
	}

	@Test
	void set_withABlankNote_storesNone() {
		foundEvent();
		foundMember();

		service.set(current, event.getId(), member.getId(), new SetAttendanceRequest(AttendanceResponse.GOING, " "));

		verify(attendances).upsert(current.groupId(), event.getId(), member.getId(), "GOING", null, "ORGANIZER",
				current.userId(), NOW);
	}

	@Test
	void set_forACancelledEvent_throwsClosed() {
		foundEvent();
		foundMember();
		event.cancel(null, NOW.minusSeconds(5));

		assertThatThrownBy(() -> service.set(current, event.getId(), member.getId(),
				new SetAttendanceRequest(AttendanceResponse.GOING, null)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("attendance.closed"));
		verify(attendances, never()).upsert(any(), any(), any(), anyString(), any(), anyString(), any(), any());
	}

	@Test
	void set_forAnotherGroupsEvent_throwsNotFound() {
		given(events.findByIdAndGroupId(event.getId(), current.groupId())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.set(current, event.getId(), member.getId(),
				new SetAttendanceRequest(AttendanceResponse.GOING, null)))
			.isInstanceOf(NotFoundException.class);
		verify(attendances, never()).upsert(any(), any(), any(), anyString(), any(), anyString(), any(), any());
	}

	@Test
	void set_forAnotherGroupsMember_throwsNotFound() {
		foundEvent();
		given(members.findByIdAndGroupId(member.getId(), current.groupId())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.set(current, event.getId(), member.getId(),
				new SetAttendanceRequest(AttendanceResponse.GOING, null)))
			.isInstanceOf(NotFoundException.class);
		verify(attendances, never()).upsert(any(), any(), any(), anyString(), any(), anyString(), any(), any());
	}

	@Test
	void clear_deletesTheAnswer_evenAfterTheEventStarted() {
		foundEvent();
		foundMember();

		service.clear(current, event.getId(), member.getId());

		verify(attendances).deleteByEventIdAndMemberId(event.getId(), member.getId());
	}

	@Test
	void clear_whenThereIsNoAnswer_stillSucceeds() {
		foundEvent();
		foundMember();

		service.clear(current, event.getId(), member.getId());
		service.clear(current, event.getId(), member.getId());

		verify(attendances, org.mockito.Mockito.times(2)).deleteByEventIdAndMemberId(event.getId(), member.getId());
	}

	@Test
	void clear_forACancelledEvent_throwsClosed() {
		foundEvent();
		foundMember();
		event.cancel(null, NOW.minusSeconds(5));

		assertThatThrownBy(() -> service.clear(current, event.getId(), member.getId()))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("attendance.closed"));
		verify(attendances, never()).deleteByEventIdAndMemberId(any(), any());
	}

	@Test
	void clear_forAnotherGroupsEventOrMember_throwsNotFound() {
		given(events.findByIdAndGroupId(event.getId(), current.groupId())).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.clear(current, event.getId(), member.getId())).isInstanceOf(NotFoundException.class);

		foundEvent();
		given(members.findByIdAndGroupId(member.getId(), current.groupId())).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.clear(current, event.getId(), member.getId())).isInstanceOf(NotFoundException.class);
		verify(attendances, never()).deleteByEventIdAndMemberId(any(), any());
	}

}
