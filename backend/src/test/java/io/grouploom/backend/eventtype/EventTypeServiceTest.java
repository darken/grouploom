package io.grouploom.backend.eventtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.dto.CreateEventTypeRequest;
import io.grouploom.backend.eventtype.dto.UpdateEventTypeRequest;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.user.Role;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventTypeServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

	@Mock EventTypeRepository repository;
	@Mock GroupRepository groups;
	@Mock EventRepository events;

	private final UUID groupId = UUID.randomUUID();
	private final CurrentUser current = new CurrentUser(UUID.randomUUID(), groupId, Role.ORGANIZER);
	private Group group;
	private EventTypeService service;

	@BeforeEach
	void setUp() {
		group = new Group("Club", NOW);
		ReflectionTestUtils.setField(group, "id", groupId);
		given(groups.getReferenceById(groupId)).willReturn(group);
		given(repository.saveAndFlush(any(EventType.class))).willAnswer(i -> i.getArgument(0));
		service = new EventTypeService(repository, groups, events, Clock.fixed(LATER, ZoneOffset.UTC));
	}

	private EventType existing(String name) {
		EventType e = new EventType(group, name, null, EventTypeColor.BLUE, null, NOW);
		ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
		given(repository.findByIdAndGroupId(e.getId(), groupId)).willReturn(Optional.of(e));
		return e;
	}

	private UpdateEventTypeRequest update(String name, boolean archived, long version) {
		return new UpdateEventTypeRequest(name, "d", EventTypeColor.RED, 60, archived, version);
	}

	@Test
	void create_withPaddedName_trimsAndSaves() {
		var response = service.create(current, new CreateEventTypeRequest("  Practice  ", "d", EventTypeColor.GREEN, 90));

		ArgumentCaptor<EventType> captor = ArgumentCaptor.forClass(EventType.class);
		verify(repository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("Practice");
		assertThat(captor.getValue().getGroup()).isSameAs(group);
		assertThat(captor.getValue().getCreatedAt()).isEqualTo(LATER);
		assertThat(response.name()).isEqualTo("Practice");
		assertThat(response.archived()).isFalse();
	}

	@Test
	void create_withDuplicateName_throwsNameTakenOnNameField() {
		given(repository.existsByGroupIdAndNameIgnoreCase(groupId, "Practice")).willReturn(true);

		assertThatThrownBy(() -> service.create(current, new CreateEventTypeRequest(" Practice ", null, EventTypeColor.BLUE, null)))
			.isInstanceOfSatisfying(ConflictException.class, e -> {
				assertThat(e.getCode()).isEqualTo("event-type.name-taken");
				assertThat(e.getField()).isEqualTo("name");
			});
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void create_whenUniqueIndexRaces_translatesToNameTaken() {
		given(repository.saveAndFlush(any(EventType.class))).willThrow(new DataIntegrityViolationException("dup"));

		assertThatThrownBy(() -> service.create(current, new CreateEventTypeRequest("Practice", null, EventTypeColor.BLUE, null)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("event-type.name-taken"));
	}

	@Test
	void update_withMatchingVersion_appliesChanges() {
		EventType e = existing("Practice");

		var response = service.update(current, e.getId(), update("Rehearsal", false, 0));

		assertThat(response.name()).isEqualTo("Rehearsal");
		assertThat(response.color()).isEqualTo(EventTypeColor.RED);
		assertThat(response.defaultDurationMinutes()).isEqualTo(60);
		assertThat(response.updatedAt()).isEqualTo(LATER);
	}

	@Test
	void update_withStaleVersion_throwsStale() {
		EventType e = existing("Practice");
		ReflectionTestUtils.setField(e, "version", 3L);

		assertThatThrownBy(() -> service.update(current, e.getId(), update("Practice", false, 2)))
			.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("event-type.stale"));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void update_renamingToAnotherTypesName_throwsNameTaken() {
		EventType e = existing("Practice");
		given(repository.existsByGroupIdAndNameIgnoreCaseAndIdNot(groupId, "Board", e.getId())).willReturn(true);

		assertThatThrownBy(() -> service.update(current, e.getId(), update("Board", false, 0)))
			.isInstanceOfSatisfying(ConflictException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("event-type.name-taken");
				assertThat(ex.getField()).isEqualTo("name");
			});
	}

	@Test
	void update_renamingToOwnNameWithDifferentCase_isAllowed() {
		EventType e = existing("practice");
		// The "IdNot" lookup excludes the type itself, so the repository reports no clash.
		given(repository.existsByGroupIdAndNameIgnoreCaseAndIdNot(groupId, "Practice", e.getId())).willReturn(false);

		var response = service.update(current, e.getId(), update("Practice", false, 0));

		assertThat(response.name()).isEqualTo("Practice");
	}

	@Test
	void update_withArchivedTrue_setsArchivedAt() {
		EventType e = existing("Practice");

		var response = service.update(current, e.getId(), update("Practice", true, 0));

		assertThat(response.archived()).isTrue();
		assertThat(e.getArchivedAt()).isEqualTo(LATER);
	}

	@Test
	void update_withArchivedFalseOnArchivedType_clearsArchivedAt() {
		EventType e = existing("Practice");
		e.archive(NOW);

		var response = service.update(current, e.getId(), update("Practice", false, 0));

		assertThat(response.archived()).isFalse();
		assertThat(e.getArchivedAt()).isNull();
	}

	@Test
	void update_whenFlushRacesOnUniqueIndex_translatesToNameTaken() {
		EventType e = existing("Practice");
		given(repository.saveAndFlush(any(EventType.class))).willThrow(new DataIntegrityViolationException("dup"));

		assertThatThrownBy(() -> service.update(current, e.getId(), update("Board", false, 0)))
			.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("event-type.name-taken"));
	}

	@Test
	void update_whenFlushDetectsConcurrentEdit_translatesToStale() {
		EventType e = existing("Practice");
		given(repository.saveAndFlush(any(EventType.class)))
			.willThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(EventType.class, e.getId()));

		assertThatThrownBy(() -> service.update(current, e.getId(), update("Board", false, 0)))
			.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("event-type.stale"));
	}

	@Test
	void getUpdateDelete_withTypeFromAnotherGroup_throwNotFound() {
		UUID foreignId = UUID.randomUUID();
		given(repository.findByIdAndGroupId(foreignId, groupId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(current, foreignId)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(current, foreignId, update("X", false, 0))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(current, foreignId)).isInstanceOf(NotFoundException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	void delete_withOwnType_deletesIt() {
		EventType e = existing("Practice");

		service.delete(current, e.getId());

		verify(repository).delete(e);
	}

	@Test
	void delete_withTypeUsedByEvents_throwsInUse() {
		EventType e = existing("Practice");
		given(events.existsByEventTypeId(e.getId())).willReturn(true);

		assertThatThrownBy(() -> service.delete(current, e.getId()))
			.isInstanceOfSatisfying(ConflictException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("event-type.in-use");
				assertThat(ex.getField()).isNull();
				assertThat(ex.getMessage()).isEqualTo("This event type is used by events. Archive it instead.");
			});
		verify(repository, never()).delete(any());
	}

	@Test
	void delete_whenAnEventAppearsBeforeTheFlush_translatesTheForeignKeyViolationToInUse() {
		EventType e = existing("Practice");
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(repository).flush();

		assertThatThrownBy(() -> service.delete(current, e.getId()))
			.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("event-type.in-use"));
	}

	@Test
	void list_withArchivedFlag_queriesTheMatchingFinderForTheGroup() {
		EventType e = existing("Practice");
		PageRequest page = PageRequest.of(0, 20);
		given(repository.findAllByGroupIdAndArchivedAtIsNull(groupId, page)).willReturn(new PageImpl<>(List.of(e), page, 1));
		given(repository.findAllByGroupIdAndArchivedAtIsNotNull(groupId, page)).willReturn(new PageImpl<>(List.of(), page, 0));

		assertThat(service.list(current, false, page).getContent()).extracting("name").containsExactly("Practice");
		assertThat(service.list(current, true, page).getContent()).isEmpty();
	}

}
