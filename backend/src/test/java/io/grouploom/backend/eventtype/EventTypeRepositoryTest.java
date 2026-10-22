package io.grouploom.backend.eventtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventTypeRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired GroupRepository groups;
	@Autowired EventTypeRepository eventTypes;
	@Autowired JdbcTemplate jdbc;

	private Group group(String name) {
		return groups.save(new Group(name, NOW));
	}

	private EventType type(Group group, String name) {
		return new EventType(group, name, null, EventTypeColor.BLUE, null, NOW);
	}

	@Test
	void save_withNameDifferingOnlyByCaseInSameGroup_violatesUniqueIndex() {
		Group g = group("A");
		eventTypes.saveAndFlush(type(g, "Practice"));

		assertThatThrownBy(() -> eventTypes.saveAndFlush(type(g, "PRACTICE")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_withSameNameInDifferentGroups_isAllowed() {
		eventTypes.saveAndFlush(type(group("A"), "Practice"));

		eventTypes.saveAndFlush(type(group("B"), "Practice"));
	}

	@Test
	void save_withUnknownColorInDatabase_violatesCheckConstraint() {
		Group g = group("A");

		assertThatThrownBy(() -> jdbc.update(
				"insert into event_types (id, group_id, name, color) values (?, ?, 'X', 'magenta')", UUID.randomUUID(), g.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_withDurationOutOfRangeInDatabase_violatesCheckConstraint() {
		Group g = group("A");

		assertThatThrownBy(() -> jdbc.update(
				"insert into event_types (id, group_id, name, color, default_duration_minutes) values (?, ?, 'X', 'blue', 4)",
				UUID.randomUUID(), g.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_persistsColorAsLowercaseKeyAndStartsAtVersionZero() {
		Group g = group("A");
		EventType saved = eventTypes.saveAndFlush(new EventType(g, "Board", "desc", EventTypeColor.TEAL, 90, NOW));

		String stored = jdbc.queryForObject("select color from event_types where id = ?", String.class, saved.getId());

		assertThat(stored).isEqualTo("teal");
		assertThat(saved.getVersion()).isZero();
		assertThat(eventTypes.findByIdAndGroupId(saved.getId(), g.getId()).orElseThrow().getColor())
			.isEqualTo(EventTypeColor.TEAL);
	}

	@Test
	void findByIdAndGroupId_withOtherGroup_returnsEmpty() {
		Group a = group("A");
		Group b = group("B");
		EventType saved = eventTypes.saveAndFlush(type(a, "Practice"));

		assertThat(eventTypes.findByIdAndGroupId(saved.getId(), a.getId())).isPresent();
		assertThat(eventTypes.findByIdAndGroupId(saved.getId(), b.getId())).isEmpty();
	}

	@Test
	void findAll_splitsActiveAndArchivedWithinGroup() {
		Group a = group("A");
		Group b = group("B");
		eventTypes.save(type(a, "Active"));
		EventType archived = type(a, "Old");
		archived.archive(NOW);
		eventTypes.save(archived);
		eventTypes.saveAndFlush(type(b, "Other group"));
		PageRequest page = PageRequest.of(0, 20, Sort.by("name"));

		assertThat(eventTypes.findAllByGroupIdAndArchivedAtIsNull(a.getId(), page).map(EventType::getName))
			.containsExactly("Active");
		assertThat(eventTypes.findAllByGroupIdAndArchivedAtIsNotNull(a.getId(), page).map(EventType::getName))
			.containsExactly("Old");
	}

	@Test
	void existsByGroupIdAndNameIgnoreCase_matchesOnlyInsideTheGroup() {
		Group a = group("A");
		Group b = group("B");
		EventType saved = eventTypes.saveAndFlush(type(a, "Practice"));

		assertThat(eventTypes.existsByGroupIdAndNameIgnoreCase(a.getId(), "practice")).isTrue();
		assertThat(eventTypes.existsByGroupIdAndNameIgnoreCase(b.getId(), "practice")).isFalse();
		assertThat(eventTypes.existsByGroupIdAndNameIgnoreCaseAndIdNot(a.getId(), "practice", saved.getId())).isFalse();
		assertThat(eventTypes.existsByGroupIdAndNameIgnoreCaseAndIdNot(a.getId(), "practice", UUID.randomUUID())).isTrue();
	}

}
