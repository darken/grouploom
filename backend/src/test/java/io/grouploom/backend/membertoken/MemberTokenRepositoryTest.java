package io.grouploom.backend.membertoken;

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
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.member.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MemberTokenRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-01-02T00:00:00Z");

	@Autowired GroupRepository groups;
	@Autowired MemberRepository members;
	@Autowired MemberTokenRepository tokens;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;

	private Member member(String email) {
		Group g = groups.save(new Group("Club", NOW));
		return members.saveAndFlush(new Member(g, "Ana", email, NOW));
	}

	private MemberToken token(Member m, String hash) {
		return tokens.saveAndFlush(
				new MemberToken(m, MemberTokenPurpose.PREFERENCES, hash, NOW.plusSeconds(3600), NOW));
	}

	@Test
	void save_withDuplicateTokenHash_violatesUniqueConstraint() {
		Member m = member("ana@example.com");
		token(m, "a".repeat(64));

		assertThatThrownBy(() -> token(m, "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findByTokenHash_loadsMemberAndGroupEagerly() {
		Member m = member("ana@example.com");
		token(m, "b".repeat(64));
		em.clear();

		MemberToken found = tokens.findByTokenHash("b".repeat(64)).orElseThrow();

		PersistenceUnitUtil util = em.getEntityManagerFactory().getPersistenceUnitUtil();
		assertThat(util.isLoaded(found, "member")).isTrue();
		assertThat(util.isLoaded(found.getMember(), "group")).isTrue();
		assertThat(found.getMember().getGroup().getName()).isEqualTo("Club");
		assertThat(found.getPurpose()).isEqualTo(MemberTokenPurpose.PREFERENCES);
	}

	@Test
	void findByTokenHash_withUnknownHash_returnsEmpty() {
		assertThat(tokens.findByTokenHash("z".repeat(64))).isEmpty();
	}

	@Test
	void deleteMember_cascadesToItsTokens() {
		Member m = member("ana@example.com");
		token(m, "c".repeat(64));

		em.flush();
		em.clear();
		members.deleteById(m.getId());
		members.flush();

		assertThat(jdbc.queryForObject("select count(*) from member_tokens", Long.class)).isZero();
	}

	@Test
	void revokeAllForMember_onlyRevokesThatMembersActiveTokens() {
		Member ana = member("ana@example.com");
		Member bob = member("bob@example.com");
		token(ana, "1".repeat(64));
		token(ana, "2".repeat(64));
		MemberToken bobs = token(bob, "3".repeat(64));

		int revoked = tokens.revokeAllForMember(ana.getId(), LATER);

		assertThat(revoked).isEqualTo(2);
		assertThat(tokens.findByTokenHash("1".repeat(64)).orElseThrow().getRevokedAt()).isEqualTo(LATER);
		assertThat(tokens.findByTokenHash("2".repeat(64)).orElseThrow().getRevokedAt()).isEqualTo(LATER);
		assertThat(tokens.findByTokenHash("3".repeat(64)).orElseThrow().getRevokedAt()).isNull();
		assertThat(bobs.getId()).isNotNull();
	}

	@Test
	void revokeAllForMember_keepsTheOriginalRevocationTimeOfAlreadyRevokedTokens() {
		Member ana = member("ana@example.com");
		MemberToken t = token(ana, "4".repeat(64));
		t.revoke(NOW);
		tokens.saveAndFlush(t);

		tokens.revokeAllForMember(ana.getId(), LATER);

		assertThat(tokens.findByTokenHash("4".repeat(64)).orElseThrow().getRevokedAt()).isEqualTo(NOW);
	}

	@Test
	void insert_withUnknownPurpose_violatesCheckConstraint() {
		Member m = member("ana@example.com");

		assertThatThrownBy(() -> jdbc.update(
				"insert into member_tokens (id, member_id, purpose, token_hash, expires_at) values (?, ?, 'ADMIN', ?, now())",
				UUID.randomUUID(), m.getId(), "d".repeat(64)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
