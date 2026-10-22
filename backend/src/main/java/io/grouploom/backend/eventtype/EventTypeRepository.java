package io.grouploom.backend.eventtype;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {

	Optional<EventType> findByIdAndGroupId(UUID id, UUID groupId);

	Page<EventType> findAllByGroupIdAndArchivedAtIsNull(UUID groupId, Pageable pageable);

	Page<EventType> findAllByGroupIdAndArchivedAtIsNotNull(UUID groupId, Pageable pageable);

	boolean existsByGroupIdAndNameIgnoreCase(UUID groupId, String name);

	boolean existsByGroupIdAndNameIgnoreCaseAndIdNot(UUID groupId, String name, UUID id);

}
