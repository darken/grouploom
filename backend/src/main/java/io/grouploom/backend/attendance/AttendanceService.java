package io.grouploom.backend.attendance;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.attendance.dto.AttendanceRowResponse;
import io.grouploom.backend.attendance.dto.AttendanceRowResponse.MemberSummary;
import io.grouploom.backend.attendance.dto.AttendanceSummaryResponse;
import io.grouploom.backend.attendance.dto.SetAttendanceRequest;
import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.member.MemberRepository;

/**
 * Organizer view of an event's attendance. Organizers can record or clear answers at any time except for a
 * cancelled event, including after it started, to correct records.
 */
@Service
public class AttendanceService {

	private final EventRepository events;
	private final MemberRepository members;
	private final AttendanceRepository attendances;
	private final Clock clock;

	public AttendanceService(EventRepository events, MemberRepository members, AttendanceRepository attendances,
			Clock clock) {
		this.events = events;
		this.members = members;
		this.attendances = attendances;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AttendanceSummaryResponse summary(CurrentUser current, UUID eventId) {
		Event event = findEvent(current, eventId);
		AttendanceSummary s = attendances.summarize(event.getId(), current.groupId());
		return new AttendanceSummaryResponse(s.getGoing(), s.getNotGoing(), s.getNoReply(), s.getMembers());
	}

	@Transactional(readOnly = true)
	public Page<AttendanceRowResponse> list(CurrentUser current, UUID eventId, AttendanceFilter filter, Pageable pageable) {
		Event event = findEvent(current, eventId);
		return attendances.findRows(event.getId(), current.groupId(), filter, pageable)
			.map(r -> new AttendanceRowResponse(new MemberSummary(r.memberId(), r.memberName(), r.memberEmail()),
					r.response(), r.note(), r.source(), r.respondedAt()));
	}

	@Transactional
	public AttendanceRowResponse set(CurrentUser current, UUID eventId, UUID memberId, SetAttendanceRequest request) {
		Event event = findEvent(current, eventId);
		Member member = findMember(current, memberId);
		requireNotCancelled(event);
		Instant now = clock.instant();
		String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
		attendances.upsert(current.groupId(), event.getId(), member.getId(), request.response().name(), note,
				AttendanceSource.ORGANIZER.name(), current.userId(), now);
		return new AttendanceRowResponse(new MemberSummary(member.getId(), member.getName(), member.getEmail()),
				request.response(), note, AttendanceSource.ORGANIZER, now);
	}

	/** Idempotent: clearing a member who has no answer succeeds too. */
	@Transactional
	public void clear(CurrentUser current, UUID eventId, UUID memberId) {
		Event event = findEvent(current, eventId);
		Member member = findMember(current, memberId);
		requireNotCancelled(event);
		attendances.deleteByEventIdAndMemberId(event.getId(), member.getId());
	}

	private static void requireNotCancelled(Event event) {
		if (event.isCancelled()) {
			throw new ConflictException("This event is cancelled.", "attendance.closed", null);
		}
	}

	private Event findEvent(CurrentUser current, UUID eventId) {
		// Scoped by group: another group's id is indistinguishable from a missing one.
		return events.findByIdAndGroupId(eventId, current.groupId())
			.orElseThrow(() -> new NotFoundException("Event not found."));
	}

	private Member findMember(CurrentUser current, UUID memberId) {
		return members.findByIdAndGroupId(memberId, current.groupId())
			.orElseThrow(() -> new NotFoundException("Member not found."));
	}

}
