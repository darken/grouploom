package io.grouploom.backend.preference;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventTypeOptOutRepository extends JpaRepository<EventTypeOptOut, EventTypeOptOutId> {

	@Query("select o.id.eventTypeId from EventTypeOptOut o where o.id.memberId = :memberId")
	Set<UUID> findEventTypeIdsByMemberId(@Param("memberId") UUID memberId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from EventTypeOptOut o where o.id.memberId = :memberId and o.id.eventTypeId in :eventTypeIds")
	int deleteByMemberIdAndEventTypeIdIn(@Param("memberId") UUID memberId,
			@Param("eventTypeIds") Collection<UUID> eventTypeIds);

}
