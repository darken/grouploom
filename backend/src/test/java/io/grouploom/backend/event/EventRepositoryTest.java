package io.grouploom.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final PageRequest PAGE = PageRequest.of(0, 20);

	@Autowired GroupRepository groups;
	@Autowired EventTypeRepository eventTypes;
	@Autowired EventRepository events;
	@Autowired JdbcTemplate jdbc;

	private Group group(String name) {
		return groups.save(new Group(name, NOW));
	}

	private EventType type(Group group, String name) {
		return eventTypes.save(new EventType(group, name, null, EventTypeColor.BLUE, null, NOW));
	}

	/** An event starting {@code startHours} from NOW and lasting {@code hours}. */
	private Event event(Group group, EventType type, String title, long startHours, long hours) {
		Instant start = NOW.plus(Duration.ofHours(startHours));
		return events.saveAndFlush(new Event(group, type, title, null, null, start, start.plus(Duration.ofHours(hours)),
				"Europe/Madrid", NOW));
	}

	@Test
	void findUpcoming_includesEventsInProgressAndFuture_excludesEnded() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		event(g, t, "ended", -5, 2);
		event(g, t, "in progress", -1, 3);
		event(g, t, "future", 4, 1);

		var page = events.findUpcoming(g.getId(), NOW, null, PAGE);

		assertThat(page.map(Event::getTitle)).containsExactly("in progress", "future");
	}

	@Test
	void findUpcoming_withEventEndingExactlyNow_includesIt() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		event(g, t, "ends now", -2, 2);

		assertThat(events.findUpcoming(g.getId(), NOW, null, PAGE).map(Event::getTitle)).containsExactly("ends now");
		assertThat(events.findPast(g.getId(), NOW, null, PAGE)).isEmpty();
	}

	@Test
	void findPast_includesOnlyEndedEvents_newestStartFirst() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		event(g, t, "oldest", -50, 1);
		event(g, t, "recent", -5, 2);
		event(g, t, "in progress", -1, 3);
		event(g, t, "future", 4, 1);

		var page = events.findPast(g.getId(), NOW, null, PAGE);

		assertThat(page.map(Event::getTitle)).containsExactly("recent", "oldest");
	}

	@Test
	void findUpcoming_sortsByStartAscendingThenId() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		event(g, t, "later", 10, 1);
		event(g, t, "sooner", 2, 1);
		Event tieA = event(g, t, "tie a", 5, 1);
		Event tieB = event(g, t, "tie b", 5, 1);

		var page = events.findUpcoming(g.getId(), NOW, null, PAGE);

		assertThat(page.map(Event::getTitle).getContent().subList(0, 1)).containsExactly("sooner");
		assertThat(page.getContent().subList(1, 3)).extracting(Event::getId)
			.isSortedAccordingTo(java.util.Comparator.naturalOrder())
			.containsExactlyInAnyOrder(tieA.getId(), tieB.getId());
		assertThat(page.getContent().get(3).getTitle()).isEqualTo("later");
	}

	@Test
	void findUpcomingAndPast_withTypeFilter_returnOnlyThatType() {
		Group g = group("A");
		EventType practice = type(g, "Practice");
		EventType board = type(g, "Board");
		event(g, practice, "p-future", 4, 1);
		event(g, board, "b-future", 5, 1);
		event(g, practice, "p-past", -10, 1);
		event(g, board, "b-past", -11, 1);

		assertThat(events.findUpcoming(g.getId(), NOW, board.getId(), PAGE).map(Event::getTitle)).containsExactly("b-future");
		assertThat(events.findPast(g.getId(), NOW, practice.getId(), PAGE).map(Event::getTitle)).containsExactly("p-past");
	}

	@Test
	void findUpcomingAndPast_areScopedByGroup() {
		Group a = group("A");
		Group b = group("B");
		event(a, type(a, "T"), "a-future", 4, 1);
		event(b, type(b, "T"), "b-future", 4, 1);
		event(b, type(b, "T2"), "b-past", -10, 1);

		assertThat(events.findUpcoming(a.getId(), NOW, null, PAGE).map(Event::getTitle)).containsExactly("a-future");
		assertThat(events.findPast(a.getId(), NOW, null, PAGE)).isEmpty();
	}

	@Test
	void findUpcoming_pagesWithTotals() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		for (int i = 0; i < 3; i++) {
			event(g, t, "e" + i, 2 + i, 1);
		}

		var second = events.findUpcoming(g.getId(), NOW, null, PageRequest.of(1, 2));

		assertThat(second.getTotalElements()).isEqualTo(3);
		assertThat(second.map(Event::getTitle)).containsExactly("e2");
	}

	@Test
	void findByIdAndGroupId_withOtherGroup_returnsEmptyAndLoadsTheTypeEagerly() {
		Group a = group("A");
		Group b = group("B");
		EventType t = type(a, "Practice");
		Event saved = event(a, t, "x", 4, 1);

		assertThat(events.findByIdAndGroupId(saved.getId(), b.getId())).isEmpty();
		Event found = events.findByIdAndGroupId(saved.getId(), a.getId()).orElseThrow();
		assertThat(found.getEventType().getName()).isEqualTo("Practice");
		assertThat(found.getTimeZone()).isEqualTo("Europe/Madrid");
		assertThat(found.getVersion()).isZero();
	}

	@Test
	void save_withEndNotAfterStart_violatesCheckConstraint() {
		Group g = group("A");
		EventType t = type(g, "Practice");

		assertThatThrownBy(() -> events.saveAndFlush(new Event(g, t, "bad", null, null, NOW, NOW, "UTC", NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void delete_ofEventTypeReferencedByAnEvent_failsAtTheDatabase() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		event(g, t, "x", 4, 1);

		// Plain SQL, so the foreign key (not Hibernate's persistence-context check) is what is tested.
		assertThatThrownBy(() -> jdbc.update("delete from event_types where id = ?", t.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void existsByEventTypeId_reportsWhetherAnyEventUsesTheType() {
		Group g = group("A");
		EventType used = type(g, "Used");
		EventType unused = type(g, "Unused");
		event(g, used, "x", 4, 1);

		assertThat(events.existsByEventTypeId(used.getId())).isTrue();
		assertThat(events.existsByEventTypeId(unused.getId())).isFalse();
		assertThat(events.existsByEventTypeId(UUID.randomUUID())).isFalse();
	}

	@Test
	void deletingAGroup_cascadesItsEventsAndEventTypesWithoutForeignKeyErrors() {
		Group g = group("A");
		EventType t = type(g, "Practice");
		event(g, t, "x", 4, 1);

		int deleted = jdbc.update("delete from groups where id = ?", g.getId());

		assertThat(deleted).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from events where group_id = ?", Integer.class, g.getId())).isZero();
		assertThat(jdbc.queryForObject("select count(*) from event_types where group_id = ?", Integer.class, g.getId())).isZero();
	}

}
