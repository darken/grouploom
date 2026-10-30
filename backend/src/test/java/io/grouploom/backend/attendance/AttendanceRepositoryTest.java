package io.grouploom.backend.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.member.MemberRepository;
import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AttendanceRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant LATER = Instant.parse("2026-06-01T13:00:00Z");

	@Autowired GroupRepository groups;
	@Autowired MemberRepository members;
	@Autowired EventTypeRepository eventTypes;
	@Autowired EventRepository events;
	@Autowired AttendanceRepository attendances;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;

	private Group group;
	private Event event;
	private EventType type;
	private int counter;

	@BeforeEach
	void setUp() {
		group = groups.save(new Group("Club", NOW));
		type = eventTypes.save(new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW));
		event = event(group, "Practice");
	}

	private Event event(Group g, String title) {
		EventType t = g == group ? type : eventTypes.save(new EventType(g, "Practice", null, EventTypeColor.GREEN, null, NOW));
		Instant start = NOW.plusSeconds(86_400);
		return events.saveAndFlush(new Event(g, t, title, null, null, start, start.plusSeconds(3600), "UTC", NOW));
	}

	private Member member(String name) {
		return members.saveAndFlush(new Member(group, name, "m" + counter++ + "@example.com", NOW));
	}

	private void answer(Event e, Member m, AttendanceResponse response, String note) {
		attendances.upsert(e.getGroup().getId(), e.getId(), m.getId(), response.name(), note, "MEMBER", null, NOW);
	}

	private long rows() {
		return jdbc.queryForObject("select count(*) from attendances", Long.class);
	}

	@Test
	void upsert_forANewPair_insertsTheRow() {
		Member ana = member("Ana");

		attendances.upsert(group.getId(), event.getId(), ana.getId(), "GOING", "On my way", "MEMBER", null, NOW);

		Attendance saved = attendances.findByEventIdAndMemberId(event.getId(), ana.getId()).orElseThrow();
		assertThat(saved.getResponse()).isEqualTo(AttendanceResponse.GOING);
		assertThat(saved.getNote()).isEqualTo("On my way");
		assertThat(saved.getSource()).isEqualTo(AttendanceSource.MEMBER);
		assertThat(saved.getRespondedAt()).isEqualTo(NOW);
		assertThat(saved.getSetBy()).isNull();
	}

	@Test
	void upsert_forAnExistingPair_updatesTheSameRowAndReplacesSourceAndNote() {
		Member ana = member("Ana");
		UUID userId = jdbc.queryForObject("insert into users (id, group_id, email, display_name, password_hash, role) "
				+ "values (gen_random_uuid(), ?, 'o@example.com', 'Olga', 'x', 'OWNER') returning id", UUID.class, group.getId());
		attendances.upsert(group.getId(), event.getId(), ana.getId(), "GOING", "Yes", "MEMBER", null, NOW);
		UUID id = jdbc.queryForObject("select id from attendances", UUID.class);

		attendances.upsert(group.getId(), event.getId(), ana.getId(), "NOT_GOING", null, "ORGANIZER", userId, LATER);
		em.clear();

		assertThat(rows()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select id from attendances", UUID.class)).isEqualTo(id);
		Attendance saved = attendances.findByEventIdAndMemberId(event.getId(), ana.getId()).orElseThrow();
		assertThat(saved.getResponse()).isEqualTo(AttendanceResponse.NOT_GOING);
		assertThat(saved.getNote()).isNull();
		assertThat(saved.getSource()).isEqualTo(AttendanceSource.ORGANIZER);
		assertThat(saved.getSetBy().getId()).isEqualTo(userId);
		assertThat(saved.getRespondedAt()).isEqualTo(LATER);
	}

	@Test
	void upsert_afterAnOrganizerAnswer_aMemberAnswerTakesOverAndClearsTheUser() {
		Member ana = member("Ana");
		UUID userId = jdbc.queryForObject("insert into users (id, group_id, email, display_name, password_hash, role) "
				+ "values (gen_random_uuid(), ?, 'o@example.com', 'Olga', 'x', 'OWNER') returning id", UUID.class, group.getId());
		attendances.upsert(group.getId(), event.getId(), ana.getId(), "GOING", null, "ORGANIZER", userId, NOW);

		attendances.upsert(group.getId(), event.getId(), ana.getId(), "NOT_GOING", null, "MEMBER", null, LATER);
		em.clear();

		Attendance saved = attendances.findByEventIdAndMemberId(event.getId(), ana.getId()).orElseThrow();
		assertThat(saved.getSource()).isEqualTo(AttendanceSource.MEMBER);
		assertThat(saved.getSetBy()).isNull();
	}

	@Test
	void insert_aSecondRowForTheSameEventAndMember_violatesTheUniqueIndex() {
		Member ana = member("Ana");
		answer(event, ana, AttendanceResponse.GOING, null);

		assertThatThrownBy(() -> jdbc.update("insert into attendances (id, group_id, event_id, member_id, response, source, responded_at) "
				+ "values (gen_random_uuid(), ?, ?, ?, 'GOING', 'MEMBER', now())", group.getId(), event.getId(), ana.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertRaw(Member m, String response, String source, String note) {
		jdbc.update("insert into attendances (id, group_id, event_id, member_id, response, source, note, responded_at) "
				+ "values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now())", group.getId(), event.getId(), m.getId(), response, source, note);
	}

	@Test
	void insert_withUnknownResponse_violatesTheCheckConstraint() {
		Member ana = member("Ana");

		assertThatThrownBy(() -> insertRaw(ana, "MAYBE", "MEMBER", null)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void insert_withUnknownSource_violatesTheCheckConstraint() {
		Member ana = member("Ana");

		assertThatThrownBy(() -> insertRaw(ana, "GOING", "ROBOT", null)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void insert_withANoteOver280Characters_isRejected() {
		Member ana = member("Ana");

		assertThatThrownBy(() -> insertRaw(ana, "GOING", "MEMBER", "x".repeat(281))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deleteMember_cascadesToTheirAnswers() {
		Member ana = member("Ana");
		Member bob = member("Bob");
		answer(event, ana, AttendanceResponse.GOING, null);
		answer(event, bob, AttendanceResponse.GOING, null);
		em.clear();

		members.deleteById(ana.getId());
		members.flush();

		assertThat(jdbc.queryForList("select member_id from attendances", UUID.class)).containsExactly(bob.getId());
	}

	@Test
	void deleteEvent_cascadesToItsAnswers() {
		Member ana = member("Ana");
		answer(event, ana, AttendanceResponse.GOING, null);

		jdbc.update("delete from events where id = ?", event.getId());

		assertThat(rows()).isZero();
	}

	@Test
	void deleteByEventIdAndMemberId_removesOnlyThatAnswer() {
		Member ana = member("Ana");
		Member bob = member("Bob");
		answer(event, ana, AttendanceResponse.GOING, null);
		answer(event, bob, AttendanceResponse.GOING, null);

		attendances.deleteByEventIdAndMemberId(event.getId(), ana.getId());
		attendances.flush();

		assertThat(jdbc.queryForList("select member_id from attendances", UUID.class)).containsExactly(bob.getId());
	}

	@Test
	void summarize_countsAnswersAndDerivesNoReplyFromCurrentMembers() {
		Member ana = member("Ana");
		Member bob = member("Bob");
		Member cy = member("Cy");
		member("Di");
		answer(event, ana, AttendanceResponse.GOING, null);
		answer(event, bob, AttendanceResponse.GOING, null);
		answer(event, cy, AttendanceResponse.NOT_GOING, null);
		// Other events and other groups don't count.
		answer(event(group, "Other"), ana, AttendanceResponse.NOT_GOING, null);
		Group other = groups.save(new Group("Other", NOW));
		members.saveAndFlush(new Member(other, "Zed", "zed@example.com", NOW));

		AttendanceSummary summary = attendances.summarize(event.getId(), group.getId());

		assertThat(summary.getGoing()).isEqualTo(2);
		assertThat(summary.getNotGoing()).isEqualTo(1);
		assertThat(summary.getMembers()).isEqualTo(4);
		assertThat(summary.getNoReply()).isEqualTo(1);
	}

	@Test
	void summarize_afterAMemberIsRemoved_dropsTheirAnswer() {
		Member ana = member("Ana");
		member("Bob");
		answer(event, ana, AttendanceResponse.GOING, null);
		em.clear();
		members.deleteById(ana.getId());
		members.flush();

		AttendanceSummary summary = attendances.summarize(event.getId(), group.getId());

		assertThat(summary.getGoing()).isZero();
		assertThat(summary.getMembers()).isEqualTo(1);
		assertThat(summary.getNoReply()).isEqualTo(1);
	}

	@Test
	void summarize_withoutAnswersOrMembers_returnsZeros() {
		AttendanceSummary summary = attendances.summarize(event.getId(), group.getId());

		assertThat(summary.getGoing()).isZero();
		assertThat(summary.getNotGoing()).isZero();
		assertThat(summary.getMembers()).isZero();
		assertThat(summary.getNoReply()).isZero();
	}

	private List<String> names(Page<AttendanceRow> page) {
		return page.getContent().stream().map(AttendanceRow::memberName).toList();
	}

	private void roster() {
		answer(event, member("Cy"), AttendanceResponse.NOT_GOING, "Sorry");
		answer(event, member("ana"), AttendanceResponse.GOING, null);
		member("Bob");
		answer(event, member("Di"), AttendanceResponse.GOING, null);
		member("Eve");
	}

	@Test
	void findRows_all_includesMembersWithoutAnAnswerOrderedByNameIgnoringCase() {
		roster();

		Page<AttendanceRow> page = attendances.findRows(event.getId(), group.getId(), AttendanceFilter.ALL, PageRequest.of(0, 50));

		assertThat(names(page)).containsExactly("ana", "Bob", "Cy", "Di", "Eve");
		assertThat(page.getTotalElements()).isEqualTo(5);
		assertThat(page.getContent().get(0).response()).isEqualTo(AttendanceResponse.GOING);
		assertThat(page.getContent().get(1).response()).isNull();
		assertThat(page.getContent().get(1).source()).isNull();
		assertThat(page.getContent().get(2).note()).isEqualTo("Sorry");
		assertThat(page.getContent().get(2).respondedAt()).isEqualTo(NOW);
		assertThat(page.getContent().get(0).memberEmail()).endsWith("@example.com");
	}

	@Test
	void findRows_going_returnsOnlyGoingMembers() {
		roster();

		assertThat(names(attendances.findRows(event.getId(), group.getId(), AttendanceFilter.GOING, PageRequest.of(0, 50))))
			.containsExactly("ana", "Di");
	}

	@Test
	void findRows_notGoing_returnsOnlyMembersWhoCannotGo() {
		roster();

		Page<AttendanceRow> page = attendances.findRows(event.getId(), group.getId(), AttendanceFilter.NOT_GOING, PageRequest.of(0, 50));

		assertThat(names(page)).containsExactly("Cy");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void findRows_noReply_returnsMembersWithoutAnAnswerForThisEventOnly() {
		roster();
		answer(event(group, "Other"), members.findAll().stream().filter(m -> m.getName().equals("Bob")).findFirst().orElseThrow(),
				AttendanceResponse.GOING, null);

		Page<AttendanceRow> page = attendances.findRows(event.getId(), group.getId(), AttendanceFilter.NO_REPLY, PageRequest.of(0, 50));

		assertThat(names(page)).containsExactly("Bob", "Eve");
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void findRows_pages_keepTheOrderAndReportTheTotal() {
		roster();

		Page<AttendanceRow> second = attendances.findRows(event.getId(), group.getId(), AttendanceFilter.ALL, PageRequest.of(1, 2));

		assertThat(names(second)).containsExactly("Cy", "Di");
		assertThat(second.getTotalElements()).isEqualTo(5);
		assertThat(second.getTotalPages()).isEqualTo(3);
	}

	@Test
	void findRows_isScopedByGroup() {
		roster();
		Group other = groups.save(new Group("Other", NOW));
		members.saveAndFlush(new Member(other, "Zed", "zed@example.com", NOW));

		Page<AttendanceRow> page = attendances.findRows(event.getId(), group.getId(), AttendanceFilter.ALL, PageRequest.of(0, 50));

		assertThat(names(page)).doesNotContain("Zed").hasSize(5);
		assertThat(attendances.findRows(event.getId(), other.getId(), AttendanceFilter.ALL, PageRequest.of(0, 50)).getContent())
			.extracting(AttendanceRow::memberName).containsExactly("Zed");
	}

	@Test
	void countGoing_isGroupedByEventAndIgnoresOtherAnswers() {
		Event second = event(group, "Second");
		Event empty = event(group, "Empty");
		Member ana = member("Ana");
		Member bob = member("Bob");
		Member cy = member("Cy");
		answer(event, ana, AttendanceResponse.GOING, null);
		answer(event, bob, AttendanceResponse.GOING, null);
		answer(event, cy, AttendanceResponse.NOT_GOING, null);
		answer(second, ana, AttendanceResponse.GOING, null);

		List<GoingCount> counts = attendances.countGoing(List.of(event.getId(), second.getId(), empty.getId()));

		assertThat(counts).containsExactlyInAnyOrder(new GoingCount(event.getId(), 2), new GoingCount(second.getId(), 1));
	}

}
