package io.grouploom.backend.member;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {

	Optional<Member> findByIdAndGroupId(UUID id, UUID groupId);

	Page<Member> findAllByGroupId(UUID groupId, Pageable pageable);

	/** {@code pattern} is a lowercase LIKE pattern whose wildcards the caller has escaped with {@code \}. */
	@Query("""
			select m from Member m
			where m.group.id = :groupId
			  and (lower(m.name) like :pattern escape '\\' or lower(m.email) like :pattern escape '\\')
			""")
	Page<Member> search(@Param("groupId") UUID groupId, @Param("pattern") String pattern, Pageable pageable);

	boolean existsByGroupIdAndEmailIgnoreCase(UUID groupId, String email);

	boolean existsByGroupIdAndEmailIgnoreCaseAndIdNot(UUID groupId, String email, UUID id);

}
