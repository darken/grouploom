package io.grouploom.backend.attendance;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.attendance.dto.AnswerRequest;
import io.grouploom.backend.attendance.dto.PublicAttendanceResponse;
import io.grouploom.backend.attendance.dto.PublicEvent;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.ResolvedAttendanceToken;

/**
 * Member replies, authorized by the attendance link token alone. The token only says who the member is and which
 * event the link is for; whether an answer is still accepted is decided here, from the event's state.
 * Both operations write ({@code lastUsedAt}), so neither is read-only.
 */
@Service
public class PublicAttendanceService {

	private final MemberTokenService tokens;
	private final AttendanceRepository attendances;
	private final Clock clock;

	public PublicAttendanceService(MemberTokenService tokens, AttendanceRepository attendances, Clock clock) {
		this.tokens = tokens;
		this.attendances = attendances;
		this.clock = clock;
	}

	@Transactional
	public PublicAttendanceResponse get(String rawToken) {
		ResolvedAttendanceToken resolved = tokens.resolveAttendance(rawToken);
		Event event = resolved.event();
		Member member = resolved.member();
		return attendances.findByEventIdAndMemberId(event.getId(), member.getId())
			.map(a -> toResponse(member, event, a.getResponse(), a.getNote(), a.getRespondedAt()))
			.orElseGet(() -> toResponse(member, event, null, null, null));
	}

	@Transactional
	public PublicAttendanceResponse answer(String rawToken, AnswerRequest request) {
		ResolvedAttendanceToken resolved = tokens.resolveAttendance(rawToken);
		Event event = resolved.event();
		Member member = resolved.member();
		Instant now = clock.instant();
		if (!isOpen(event, now)) {
			throw new ConflictException("Replies are closed for this event.", "attendance.closed", null);
		}
		String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
		attendances.upsert(event.getGroup().getId(), event.getId(), member.getId(), request.response().name(), note,
				AttendanceSource.MEMBER.name(), null, now);
		return toResponse(member, event, request.response(), note, now);
	}

	private static boolean isOpen(Event event, Instant now) {
		return !event.isCancelled() && event.getStartsAt().isAfter(now);
	}

	private PublicAttendanceResponse toResponse(Member member, Event event, AttendanceResponse response, String note,
			Instant respondedAt) {
		EventType type = event.getEventType();
		var zone = event.getZoneId();
		PublicEvent publicEvent = new PublicEvent(event.getTitle(), event.getDescription(), event.getLocation(),
				event.getStartsAt(), event.getEndsAt(), event.getStartsAt().atZone(zone).toLocalDateTime(),
				event.getEndsAt().atZone(zone).toLocalDateTime(), event.getTimeZone(), type.getName(), type.getColor(),
				event.isCancelled(), event.getCancellationMessage());
		return new PublicAttendanceResponse(member.getGroup().getName(), member.getName(), publicEvent, response, note,
				respondedAt, isOpen(event, clock.instant()));
	}

}
