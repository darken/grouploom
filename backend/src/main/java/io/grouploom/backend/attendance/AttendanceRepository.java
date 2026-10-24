package io.grouploom.backend.attendance;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

	Optional<Attendance> findByEventIdAndMemberId(UUID eventId, UUID memberId);

	/**
	 * Sets a member's answer, replacing any earlier one. A single statement on the unique (event, member) pair, so
	 * two quick taps can't race into a duplicate-key error. {@code source} is the enum name.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO attendances (id, group_id, event_id, member_id, response, note, source, set_by_user_id,
			                         responded_at, created_at)
			VALUES (gen_random_uuid(), :groupId, :eventId, :memberId, :response, :note, :source,
			        CAST(:setByUserId AS uuid), :now, :now)
			ON CONFLICT (event_id, member_id) DO UPDATE SET
			    response = EXCLUDED.response, note = EXCLUDED.note, source = EXCLUDED.source,
			    set_by_user_id = EXCLUDED.set_by_user_id, responded_at = EXCLUDED.responded_at
			""")
	int upsert(@Param("groupId") UUID groupId, @Param("eventId") UUID eventId, @Param("memberId") UUID memberId,
			@Param("response") String response, @Param("note") String note, @Param("source") String source,
			@Param("setByUserId") UUID setByUserId, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	void deleteByEventIdAndMemberId(UUID eventId, UUID memberId);

	/** One aggregate row (never empty), so the counts come from one query. */
	@Query(nativeQuery = true, value = """
			SELECT count(*) FILTER (WHERE a.response = 'GOING') AS going,
			       count(*) FILTER (WHERE a.response = 'NOT_GOING') AS notGoing,
			       (SELECT count(*) FROM members WHERE group_id = :groupId) AS members
			FROM attendances a
			WHERE a.event_id = :eventId AND a.group_id = :groupId
			""")
	AttendanceSummary summarize(@Param("eventId") UUID eventId, @Param("groupId") UUID groupId);

	/** Every current member of the group with their answer (if any), by name; one query per page. */
	default Page<AttendanceRow> findRows(UUID eventId, UUID groupId, AttendanceFilter filter, Pageable pageable) {
		return switch (filter) {
			case ALL -> findRows(eventId, groupId, true, false, null, pageable);
			case NO_REPLY -> findRows(eventId, groupId, false, true, null, pageable);
			case GOING -> findRows(eventId, groupId, false, false, AttendanceResponse.GOING, pageable);
			case NOT_GOING -> findRows(eventId, groupId, false, false, AttendanceResponse.NOT_GOING, pageable);
		};
	}

	@Query(value = """
			select new io.grouploom.backend.attendance.AttendanceRow(m.id, m.name, m.email, a.response, a.note, a.source,
			                                                         a.respondedAt)
			from Member m left join Attendance a on a.member = m and a.event.id = :eventId
			where m.group.id = :groupId
			  and (:all = true or (:noReply = true and a.id is null) or (:noReply = false and a.response = :response))
			order by lower(m.name), m.id
			""", countQuery = """
			select count(m) from Member m left join Attendance a on a.member = m and a.event.id = :eventId
			where m.group.id = :groupId
			  and (:all = true or (:noReply = true and a.id is null) or (:noReply = false and a.response = :response))
			""")
	Page<AttendanceRow> findRows(@Param("eventId") UUID eventId, @Param("groupId") UUID groupId,
			@Param("all") boolean all, @Param("noReply") boolean noReply,
			@Param("response") AttendanceResponse response, Pageable pageable);

	/** Going counts for a page of events in one query; events nobody is going to are absent. */
	@Query("""
			select new io.grouploom.backend.attendance.GoingCount(a.event.id, count(a)) from Attendance a
			where a.event.id in :eventIds and a.response = io.grouploom.backend.attendance.AttendanceResponse.GOING
			group by a.event.id
			""")
	List<GoingCount> countGoing(@Param("eventIds") Collection<UUID> eventIds);

}
