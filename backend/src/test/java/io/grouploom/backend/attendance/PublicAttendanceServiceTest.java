package io.grouploom.backend.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.attendance.dto.AnswerRequest;
import io.grouploom.backend.attendance.dto.PublicAttendanceResponse;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.ResolvedAttendanceToken;

@ExtendWith(MockitoExtension.class)
class PublicAttendanceServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Instant START = Instant.parse("2026-11-07T09:00:00Z");

	@Mock MemberTokenService tokens;
	@Mock AttendanceRepository attendances;

	private PublicAttendanceService service;
	private Group group;
	private Member member;
	private Event event;

	@BeforeEach
	void setUp() {
		service = new PublicAttendanceService(tokens, attendances, Clock.fixed(NOW, ZoneOffset.UTC));
		group = new Group("Chess Club", NOW);
		ReflectionTestUtils.setField(group, "id", UUID.randomUUID());
		member = new Member(group, "Ana", "ana@example.com", NOW);
		ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
		EventType type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW);
		event = new Event(group, type, "Practice", "Bring a board", "Main hall", START, START.plusSeconds(7200), "Europe/Madrid", NOW);
		ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
		lenient().when(tokens.resolveAttendance("raw")).thenReturn(new ResolvedAttendanceToken(member, event));
	}

	private Attendance answered(AttendanceResponse response, String note) {
		Attendance a = BeanUtils.instantiateClass(Attendance.class);
		ReflectionTestUtils.setField(a, "response", response);
		ReflectionTestUtils.setField(a, "note", note);
		ReflectionTestUtils.setField(a, "respondedAt", NOW.minusSeconds(60));
		return a;
	}

	private void startedAlready() {
		ReflectionTestUtils.setField(event, "startsAt", NOW.minusSeconds(1));
	}

	@Test
	void get_returnsTheEventDetailsTheCurrentAnswerAndOpen() {
		given(attendances.findByEventIdAndMemberId(event.getId(), member.getId()))
			.willReturn(Optional.of(answered(AttendanceResponse.GOING, "See you")));

		PublicAttendanceResponse response = service.get("raw");

		assertThat(response.groupName()).isEqualTo("Chess Club");
		assertThat(response.memberName()).isEqualTo("Ana");
		assertThat(response.event().title()).isEqualTo("Practice");
		assertThat(response.event().description()).isEqualTo("Bring a board");
		assertThat(response.event().location()).isEqualTo("Main hall");
		assertThat(response.event().start()).isEqualTo(START);
		assertThat(response.event().localStart().toString()).isEqualTo("2026-11-07T10:00");
		assertThat(response.event().localEnd().toString()).isEqualTo("2026-11-07T12:00");
		assertThat(response.event().timeZone()).isEqualTo("Europe/Madrid");
		assertThat(response.event().typeName()).isEqualTo("Practice");
		assertThat(response.event().typeColor()).isEqualTo(EventTypeColor.GREEN);
		assertThat(response.event().cancelled()).isFalse();
		assertThat(response.response()).isEqualTo(AttendanceResponse.GOING);
		assertThat(response.note()).isEqualTo("See you");
		assertThat(response.respondedAt()).isEqualTo(NOW.minusSeconds(60));
		assertThat(response.open()).isTrue();
	}

	@Test
	void get_withoutAnAnswer_hasNoResponse() {
		given(attendances.findByEventIdAndMemberId(event.getId(), member.getId())).willReturn(Optional.empty());

		PublicAttendanceResponse response = service.get("raw");

		assertThat(response.response()).isNull();
		assertThat(response.note()).isNull();
		assertThat(response.respondedAt()).isNull();
		assertThat(response.open()).isTrue();
	}

	@Test
	void get_afterTheEventStarted_isNotOpenButStillShowsTheAnswer() {
		startedAlready();
		given(attendances.findByEventIdAndMemberId(event.getId(), member.getId()))
			.willReturn(Optional.of(answered(AttendanceResponse.NOT_GOING, null)));

		PublicAttendanceResponse response = service.get("raw");

		assertThat(response.open()).isFalse();
		assertThat(response.response()).isEqualTo(AttendanceResponse.NOT_GOING);
	}

	@Test
	void get_forACancelledEvent_isNotOpenAndCarriesTheMessage() {
		event.cancel("The gym is closed", NOW.minusSeconds(5));
		given(attendances.findByEventIdAndMemberId(event.getId(), member.getId())).willReturn(Optional.empty());

		PublicAttendanceResponse response = service.get("raw");

		assertThat(response.open()).isFalse();
		assertThat(response.event().cancelled()).isTrue();
		assertThat(response.event().cancellationMessage()).isEqualTo("The gym is closed");
	}

	@Test
	void get_withABadToken_propagatesTheTokenError() {
		given(tokens.resolveAttendance("bad")).willThrow(new InvalidMemberTokenException("member-token.expired"));

		assertThatThrownBy(() -> service.get("bad")).isInstanceOfSatisfying(InvalidMemberTokenException.class,
				e -> assertThat(e.getCode()).isEqualTo("member-token.expired"));
		verifyNoInteractions(attendances);
	}

	@Test
	void answer_upsertsWithSourceMemberAndTrimsTheNote() {
		PublicAttendanceResponse response = service.answer("raw", new AnswerRequest(AttendanceResponse.GOING, "  Bringing snacks \n"));

		verify(attendances).upsert(group.getId(), event.getId(), member.getId(), "GOING", "Bringing snacks", "MEMBER", null, NOW);
		assertThat(response.response()).isEqualTo(AttendanceResponse.GOING);
		assertThat(response.note()).isEqualTo("Bringing snacks");
		assertThat(response.respondedAt()).isEqualTo(NOW);
		assertThat(response.open()).isTrue();
		assertThat(response.memberName()).isEqualTo("Ana");
	}

	@Test
	void answer_withABlankNote_storesNone() {
		service.answer("raw", new AnswerRequest(AttendanceResponse.NOT_GOING, "   "));

		verify(attendances).upsert(group.getId(), event.getId(), member.getId(), "NOT_GOING", null, "MEMBER", null, NOW);
	}

	@Test
	void answer_afterTheEventStarted_isRefusedAsClosed() {
		startedAlready();

		assertThatThrownBy(() -> service.answer("raw", new AnswerRequest(AttendanceResponse.GOING, null)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("attendance.closed"));
		verify(attendances, never()).upsert(any(), any(), any(), anyString(), any(), anyString(), any(), any());
	}

	@Test
	void answer_atTheExactStart_isRefusedAsClosed() {
		ReflectionTestUtils.setField(event, "startsAt", NOW);

		assertThatThrownBy(() -> service.answer("raw", new AnswerRequest(AttendanceResponse.GOING, null)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("attendance.closed"));
	}

	@Test
	void answer_forACancelledEvent_isRefusedAsClosed() {
		event.cancel(null, NOW.minusSeconds(5));

		assertThatThrownBy(() -> service.answer("raw", new AnswerRequest(AttendanceResponse.GOING, null)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("attendance.closed"));
		verify(attendances, never()).upsert(any(), any(), any(), anyString(), any(), anyString(), any(), any());
	}

	@Test
	void answer_withABadToken_propagatesTheTokenErrorAndWritesNothing() {
		given(tokens.resolveAttendance("bad")).willThrow(new InvalidMemberTokenException("member-token.invalid"));

		assertThatThrownBy(() -> service.answer("bad", new AnswerRequest(AttendanceResponse.GOING, null)))
			.isInstanceOf(InvalidMemberTokenException.class);
		verifyNoInteractions(attendances);
	}

}
