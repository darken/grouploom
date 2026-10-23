package io.grouploom.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MemberRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired GroupRepository groups;
	@Autowired MemberRepository members;

	private Group group(String name) {
		return groups.save(new Group(name, NOW));
	}

	private Member member(Group group, String name, String email) {
		return members.saveAndFlush(new Member(group, name, email, NOW));
	}

	@Test
	void save_withEmailDifferingOnlyByCaseInSameGroup_violatesUniqueIndex() {
		Group g = group("A");
		member(g, "Ana", "ana@example.com");

		assertThatThrownBy(() -> member(g, "Ana Two", "ANA@example.com"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_withSameEmailInDifferentGroups_isAllowed() {
		member(group("A"), "Ana", "ana@example.com");

		member(group("B"), "Ana", "ana@example.com");
	}

	@Test
	void existsByGroupIdAndEmailIgnoreCase_withDifferentCase_findsIt() {
		Group g = group("A");
		member(g, "Ana", "ana@example.com");

		assertThat(members.existsByGroupIdAndEmailIgnoreCase(g.getId(), "ANA@example.com")).isTrue();
		assertThat(members.existsByGroupIdAndEmailIgnoreCase(group("B").getId(), "ana@example.com")).isFalse();
	}

	@Test
	void existsByGroupIdAndEmailIgnoreCaseAndIdNot_excludesTheMemberItself() {
		Group g = group("A");
		Member ana = member(g, "Ana", "ana@example.com");

		assertThat(members.existsByGroupIdAndEmailIgnoreCaseAndIdNot(g.getId(), "ana@example.com", ana.getId())).isFalse();
		assertThat(members.existsByGroupIdAndEmailIgnoreCaseAndIdNot(g.getId(), "ana@example.com", java.util.UUID.randomUUID())).isTrue();
	}

	@Test
	void findByIdAndGroupId_withOtherGroup_returnsEmpty() {
		Group a = group("A");
		Member ana = member(a, "Ana", "ana@example.com");

		assertThat(members.findByIdAndGroupId(ana.getId(), a.getId())).isPresent();
		assertThat(members.findByIdAndGroupId(ana.getId(), group("B").getId())).isEmpty();
	}

	@Test
	void search_withMixedCaseTerm_matchesNameOrEmailCaseInsensitively() {
		Group g = group("A");
		member(g, "Ana Pérez", "contact@example.com");
		member(g, "Bob", "ANA.work@example.com");
		member(g, "Carl", "carl@example.com");
		member(group("B"), "Anabel", "anabel@example.com");

		Page<Member> page = members.search(g.getId(), "%ana%", PageRequest.of(0, 20, Sort.by(Sort.Order.asc("name").ignoreCase())));

		assertThat(page.getContent()).extracting(Member::getName).containsExactly("Ana Pérez", "Bob");
	}

	@Test
	void search_withEscapedPercentAndUnderscore_matchesThemLiterally() {
		Group g = group("A");
		member(g, "100% Fun", "fun@example.com");
		member(g, "Plain", "plain@example.com");
		member(g, "snake_case", "snake@example.com");
		member(g, "snakeXcase", "other@example.com");

		assertThat(members.search(g.getId(), "%100\\%%", PageRequest.of(0, 20)).getContent())
			.extracting(Member::getName).containsExactly("100% Fun");
		assertThat(members.search(g.getId(), "%snake\\_case%", PageRequest.of(0, 20)).getContent())
			.extracting(Member::getName).containsExactly("snake_case");
	}

	@Test
	void findAllByGroupId_withPagingAndNameSort_returnsOrderedSlicesOfOneGroup() {
		Group g = group("A");
		member(g, "Cara", "c@example.com");
		member(g, "ana", "a@example.com");
		member(g, "Bea", "b@example.com");
		member(group("B"), "Zed", "z@example.com");

		Sort byName = Sort.by(Sort.Order.asc("name").ignoreCase());
		Page<Member> first = members.findAllByGroupId(g.getId(), PageRequest.of(0, 2, byName));
		Page<Member> second = members.findAllByGroupId(g.getId(), PageRequest.of(1, 2, byName));

		assertThat(first.getTotalElements()).isEqualTo(3);
		assertThat(first.getContent()).extracting(Member::getName).containsExactly("ana", "Bea");
		assertThat(second.getContent()).extracting(Member::getName).containsExactly("Cara");
	}

}
