package io.grouploom.backend.membertoken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberTokenRepository extends JpaRepository<MemberToken, UUID> {

	// Attendance tokens carry their event; the reply page shows it with its type, so load both in this one query.
	@EntityGraph(attributePaths = { "member", "member.group", "event", "event.eventType" })
	Optional<MemberToken> findByTokenHash(String tokenHash);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update MemberToken t set t.revokedAt = :now where t.member.id = :memberId and t.revokedAt is null")
	int revokeAllForMember(@Param("memberId") UUID memberId, @Param("now") Instant now);

}
