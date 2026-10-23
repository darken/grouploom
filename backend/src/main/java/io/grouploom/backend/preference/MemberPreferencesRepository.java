package io.grouploom.backend.preference;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPreferencesRepository extends JpaRepository<MemberPreferences, UUID> {

	@Query("""
			select p.memberId from MemberPreferences p
			where p.member.group.id = :groupId and p.invitationsPausedAt is not null
			""")
	Set<UUID> findPausedMemberIds(@Param("groupId") UUID groupId);

}
