package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
class EmailOutboxRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant CLAIM_UNTIL = NOW.plus(Duration.ofMinutes(2));

	@Autowired GroupRepository groups;
	@Autowired MemberRepository members;
	@Autowired EmailOutboxRepository outbox;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;
	@Autowired PlatformTransactionManager txManager;

	private Group group;

	private Member member(String email) {
		if (group == null) {
			group = groups.save(new Group("Club", NOW));
		}
		return members.saveAndFlush(new Member(group, "Ana", email, NOW));
	}

	private EmailOutboxEntry entry(Member m, Instant createdAt) {
		return outbox.saveAndFlush(new EmailOutboxEntry(m.getGroup(), m, null, EmailKind.PREFERENCES_LINK, createdAt));
	}

	@Test
	void claimDue_returnsDueRowsAndMarksThemSending() {
		EmailOutboxEntry due = entry(member("a@example.com"), NOW.minusSeconds(10));

		List<UUID> claimed = outbox.claimDue(NOW, CLAIM_UNTIL, 20);

		assertThat(claimed).containsExactly(due.getId());
		assertThat(jdbc.queryForObject("select status from email_outbox where id = ?", String.class, due.getId()))
			.isEqualTo("SENDING");
		assertThat(jdbc.queryForObject("select claimed_until from email_outbox where id = ?",
				Timestamp.class, due.getId()).toInstant()).isEqualTo(CLAIM_UNTIL);
	}

	@Test
	void claimDue_skipsFutureSentAndStillClaimedRows() {
		Member m = member("a@example.com");
		EmailOutboxEntry future = entry(m, NOW);
		jdbc.update("update email_outbox set next_attempt_at = ? where id = ?",
				Timestamp.from(NOW.plusSeconds(60)), future.getId());
		EmailOutboxEntry sent = entry(m, NOW.minusSeconds(10));
		jdbc.update("update email_outbox set status = 'SENT' where id = ?", sent.getId());
		EmailOutboxEntry inFlight = entry(m, NOW.minusSeconds(10));
		jdbc.update("update email_outbox set status = 'SENDING', claimed_until = ? where id = ?",
				Timestamp.from(NOW.plusSeconds(30)), inFlight.getId());
		EmailOutboxEntry failed = entry(m, NOW.minusSeconds(10));
		jdbc.update("update email_outbox set status = 'FAILED' where id = ?", failed.getId());

		assertThat(outbox.claimDue(NOW, CLAIM_UNTIL, 20)).isEmpty();
	}

	@Test
	void claimDue_reclaimsRowsWhoseClaimExpired() {
		EmailOutboxEntry crashed = entry(member("a@example.com"), NOW.minusSeconds(600));
		jdbc.update("update email_outbox set status = 'SENDING', claimed_until = ? where id = ?",
				Timestamp.from(NOW.minusSeconds(1)), crashed.getId());

		assertThat(outbox.claimDue(NOW, CLAIM_UNTIL, 20)).containsExactly(crashed.getId());
	}

	@Test
	void claimDue_respectsLimitAndClaimsOldestDueFirst() {
		Member m = member("a@example.com");
		EmailOutboxEntry first = entry(m, NOW.minusSeconds(30));
		EmailOutboxEntry second = entry(m, NOW.minusSeconds(20));
		entry(m, NOW.minusSeconds(10));

		assertThat(outbox.claimDue(NOW, CLAIM_UNTIL, 2)).containsExactlyInAnyOrder(first.getId(), second.getId());
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void claimDue_inTwoConcurrentTransactions_neverReturnsTheSameId() throws Exception {
		TransactionTemplate tx = new TransactionTemplate(txManager);
		try {
			tx.executeWithoutResult(s -> {
				Member m = member("a@example.com");
				entry(m, NOW.minusSeconds(30));
				entry(m, NOW.minusSeconds(20));
			});
			CountDownLatch firstHasClaimed = new CountDownLatch(1);
			CountDownLatch secondDone = new CountDownLatch(1);
			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<List<UUID>> first = pool.submit(() -> tx.execute(s -> {
					List<UUID> ids = outbox.claimDue(NOW, CLAIM_UNTIL, 1);
					firstHasClaimed.countDown();
					await(secondDone);
					return ids;
				}));
				await(firstHasClaimed);
				Future<List<UUID>> second = pool.submit(() -> tx.execute(s -> outbox.claimDue(NOW, CLAIM_UNTIL, 1)));
				List<UUID> secondIds = second.get(10, TimeUnit.SECONDS);
				secondDone.countDown();
				List<UUID> firstIds = first.get(10, TimeUnit.SECONDS);

				assertThat(firstIds).hasSize(1);
				assertThat(secondIds).hasSize(1);
				assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
			}
			finally {
				secondDone.countDown();
				pool.shutdownNow();
			}
		}
		finally {
			jdbc.update("delete from groups");
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("latch timeout");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	@Test
	void deleteMember_cascadesToItsQueuedEmails() {
		Member m = member("a@example.com");
		entry(m, NOW);
		em.flush();
		em.clear();

		members.deleteById(m.getId());
		members.flush();

		assertThat(jdbc.queryForObject("select count(*) from email_outbox", Long.class)).isZero();
	}

	@Test
	void existsByMemberIdAndKindAndCreatedAtAfterAndStatusNot_ignoresFailedAndOldEntries() {
		Member m = member("a@example.com");
		EmailOutboxEntry failed = entry(m, NOW);
		failed.fail(NOW, "x");
		outbox.saveAndFlush(failed);
		entry(m, NOW.minusSeconds(600));

		assertThat(outbox.existsByMemberIdAndKindAndCreatedAtAfterAndStatusNot(m.getId(),
				EmailKind.PREFERENCES_LINK, NOW.minusSeconds(300), EmailStatus.FAILED)).isFalse();

		entry(m, NOW.minusSeconds(60));

		assertThat(outbox.existsByMemberIdAndKindAndCreatedAtAfterAndStatusNot(m.getId(),
				EmailKind.PREFERENCES_LINK, NOW.minusSeconds(300), EmailStatus.FAILED)).isTrue();
	}

	@Test
	void findMemberIdsEmailedSince_returnsOnlyMembersWithARecentNonFailedEntry() {
		Member recent = member("a@example.com");
		Member old = member("b@example.com");
		Member failed = member("c@example.com");
		Member never = member("d@example.com");
		entry(recent, NOW.minusSeconds(60));
		entry(old, NOW.minusSeconds(600));
		EmailOutboxEntry f = entry(failed, NOW.minusSeconds(60));
		f.fail(NOW, "x");
		outbox.saveAndFlush(f);

		assertThat(outbox.findMemberIdsEmailedSince(
				List.of(recent.getId(), old.getId(), failed.getId(), never.getId()), EmailKind.PREFERENCES_LINK,
				NOW.minusSeconds(300))).containsExactly(recent.getId());
	}

	@Test
	void findLatestStatus_returnsNewestEntryPerMember() {
		Member ana = member("a@example.com");
		Member bob = member("b@example.com");
		Member cy = member("c@example.com");
		EmailOutboxEntry old = entry(ana, NOW.minusSeconds(600));
		old.markSent(NOW.minusSeconds(590));
		outbox.saveAndFlush(old);
		entry(ana, NOW.minusSeconds(10));
		EmailOutboxEntry bobs = entry(bob, NOW.minusSeconds(100));
		bobs.markSent(NOW.minusSeconds(90));
		outbox.saveAndFlush(bobs);

		List<LatestEmailStatus> latest = outbox.findLatestStatus(group.getId(),
				List.of(ana.getId(), bob.getId(), cy.getId()), EmailKind.PREFERENCES_LINK.name());

		assertThat(latest).hasSize(2);
		LatestEmailStatus forAna = latest.stream().filter(l -> l.getMemberId().equals(ana.getId())).findFirst()
			.orElseThrow();
		assertThat(forAna.getStatus()).isEqualTo("PENDING");
		assertThat(forAna.getCreatedAt()).isEqualTo(NOW.minusSeconds(10));
		LatestEmailStatus forBob = latest.stream().filter(l -> l.getMemberId().equals(bob.getId())).findFirst()
			.orElseThrow();
		assertThat(forBob.getStatus()).isEqualTo("SENT");
		assertThat(forBob.getSentAt()).isEqualTo(NOW.minusSeconds(90));
	}

	@Test
	void findWithDetailsById_loadsMemberGroupAndRequesterEagerly() {
		Member m = member("a@example.com");
		EmailOutboxEntry e = entry(m, NOW);
		em.clear();

		EmailOutboxEntry found = outbox.findWithDetailsById(e.getId()).orElseThrow();

		PersistenceUnitUtil util = em.getEntityManagerFactory().getPersistenceUnitUtil();
		assertThat(util.isLoaded(found, "member")).isTrue();
		assertThat(util.isLoaded(found, "group")).isTrue();
		assertThat(util.isLoaded(found, "requestedBy")).isTrue();
		assertThat(found.getMember().getEmail()).isEqualTo("a@example.com");
	}

	@Test
	void insert_withUnknownKind_violatesCheckConstraint() {
		Member m = member("a@example.com");

		assertThatThrownBy(() -> jdbc.update(
				"insert into email_outbox (id, group_id, member_id, kind, next_attempt_at) values (?, ?, ?, 'SPAM', now())",
				UUID.randomUUID(), group.getId(), m.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void insert_withUnknownStatus_violatesCheckConstraint() {
		Member m = member("a@example.com");

		assertThatThrownBy(() -> jdbc.update(
				"insert into email_outbox (id, group_id, member_id, kind, status, next_attempt_at) values (?, ?, ?, 'PREFERENCES_LINK', 'LOST', now())",
				UUID.randomUUID(), group.getId(), m.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
