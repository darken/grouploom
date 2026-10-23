package io.grouploom.backend.event;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, UUID> {

	@EntityGraph(attributePaths = "eventType")
	Optional<Event> findByIdAndGroupId(UUID id, UUID groupId);

	/** Events that haven't ended yet (so ones in progress still show), soonest start first. */
	@EntityGraph(attributePaths = "eventType")
	@Query("""
			select e from Event e
			where e.group.id = :groupId and e.endsAt >= :now
			  and (:eventTypeId is null or e.eventType.id = :eventTypeId)
			order by e.startsAt asc, e.id asc
			""")
	Page<Event> findUpcoming(@Param("groupId") UUID groupId, @Param("now") Instant now,
			@Param("eventTypeId") UUID eventTypeId, Pageable pageable);

	/** Events that have ended, most recent start first. */
	@EntityGraph(attributePaths = "eventType")
	@Query("""
			select e from Event e
			where e.group.id = :groupId and e.endsAt < :now
			  and (:eventTypeId is null or e.eventType.id = :eventTypeId)
			order by e.startsAt desc, e.id desc
			""")
	Page<Event> findPast(@Param("groupId") UUID groupId, @Param("now") Instant now,
			@Param("eventTypeId") UUID eventTypeId, Pageable pageable);

	boolean existsByEventTypeId(UUID eventTypeId);

	/**
	 * Reads the event with a shared row lock, so a fan-out and a cancellation of the same event serialize: either
	 * the fan-out sees the cancellation, or the cancellation waits and then sees what was sent.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select e from Event e where e.id = :id")
	Optional<Event> findByIdForShare(@Param("id") UUID id);

}
