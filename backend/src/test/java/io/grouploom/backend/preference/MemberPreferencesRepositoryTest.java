package io.grouploom.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
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
class MemberPreferencesRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired GroupRepository groups;
	@Autowired MemberRepository members;
	@Autowired EventTypeRepository eventTypes;
	@Autowired MemberPreferencesRepository preferences;
	@Autowired EventTypeOptOutRepository optOuts;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;

	private Group group(String name) {
		return groups.save(new Group(name, NOW));
	}

	private Member member(Group g, String email) {
		return members.saveAndFlush(new Member(g, "Ana", email, NOW));
	}

	private EventType type(Group g, String name) {
		return eventTypes.saveAndFlush(new EventType(g, name, null, EventTypeColor.BLUE, null, NOW));
	}

	private void pause(Member m, boolean paused) {
		MemberPreferences p = new MemberPreferences(m, NOW);
		if (paused) {
			p.setInvitationsPaused(true, NOW);
		}
		preferences.saveAndFlush(p);
	}

	private long count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Long.class);
	}

	@Test
	void findPausedMemberIds_isScopedByGroupAndIgnoresUnpausedMembers() {
		Group a = group("A");
		Group b = group("B");
		Member pausedA = member(a, "p@example.com");
		Member activeA = member(a, "q@example.com");
		Member noRowA = member(a, "r@example.com");
		Member pausedB = member(b, "p@example.com");
		pause(pausedA, true);
		pause(activeA, false);
		pause(pausedB, true);

		Set<java.util.UUID> ids = preferences.findPausedMemberIds(a.getId());

		assertThat(ids).containsExactly(pausedA.getId());
		assertThat(ids).doesNotContain(activeA.getId(), noRowA.getId(), pausedB.getId());
	}

	@Test
	void findById_usesTheMemberIdAsKey() {
		Member m = member(group("A"), "p@example.com");
		pause(m, true);

		MemberPreferences found = preferences.findById(m.getId()).orElseThrow();

		assertThat(found.isInvitationsPaused()).isTrue();
		assertThat(found.getInvitationsPausedAt()).isEqualTo(NOW);
		assertThat(preferences.findById(java.util.UUID.randomUUID())).isEmpty();
	}

	@Test
	void findEventTypeIdsByMemberId_returnsOnlyThatMembersOptOuts() {
		Group g = group("A");
		Member ana = member(g, "ana@example.com");
		Member bob = member(g, "bob@example.com");
		EventType practice = type(g, "Practice");
		EventType game = type(g, "Game");
		optOuts.saveAllAndFlush(List.of(new EventTypeOptOut(ana.getId(), practice.getId(), NOW),
				new EventTypeOptOut(bob.getId(), game.getId(), NOW)));

		assertThat(optOuts.findEventTypeIdsByMemberId(ana.getId())).containsExactly(practice.getId());
	}

	@Test
	void deleteByMemberIdAndEventTypeIdIn_removesOnlyTheGivenPairs() {
		Group g = group("A");
		Member ana = member(g, "ana@example.com");
		Member bob = member(g, "bob@example.com");
		EventType practice = type(g, "Practice");
		EventType game = type(g, "Game");
		optOuts.saveAllAndFlush(List.of(new EventTypeOptOut(ana.getId(), practice.getId(), NOW),
				new EventTypeOptOut(ana.getId(), game.getId(), NOW),
				new EventTypeOptOut(bob.getId(), practice.getId(), NOW)));

		optOuts.deleteByMemberIdAndEventTypeIdIn(ana.getId(), Set.of(practice.getId()));

		assertThat(optOuts.findEventTypeIdsByMemberId(ana.getId())).containsExactly(game.getId());
		assertThat(optOuts.findEventTypeIdsByMemberId(bob.getId())).containsExactly(practice.getId());
	}

	@Test
	void deleteMember_cascadesToPreferencesAndOptOuts() {
		Group g = group("A");
		Member ana = member(g, "ana@example.com");
		EventType practice = type(g, "Practice");
		pause(ana, true);
		optOuts.saveAndFlush(new EventTypeOptOut(ana.getId(), practice.getId(), NOW));

		em.clear();
		members.deleteById(ana.getId());
		members.flush();

		assertThat(count("member_preferences")).isZero();
		assertThat(count("member_event_type_opt_outs")).isZero();
	}

	@Test
	void deleteEventType_cascadesToOptOutsButKeepsPreferences() {
		Group g = group("A");
		Member ana = member(g, "ana@example.com");
		EventType practice = type(g, "Practice");
		pause(ana, true);
		optOuts.saveAndFlush(new EventTypeOptOut(ana.getId(), practice.getId(), NOW));

		em.clear();
		eventTypes.deleteById(practice.getId());
		eventTypes.flush();

		assertThat(count("member_event_type_opt_outs")).isZero();
		assertThat(count("member_preferences")).isOne();
	}

	@Test
	void insertOptOut_forTheSameMemberAndTypeTwice_violatesCompositePrimaryKey() {
		Group g = group("A");
		Member ana = member(g, "ana@example.com");
		EventType practice = type(g, "Practice");
		String sql = "insert into member_event_type_opt_outs (member_id, event_type_id) values (?, ?)";
		jdbc.update(sql, ana.getId(), practice.getId());

		assertThatThrownBy(() -> jdbc.update(sql, ana.getId(), practice.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
