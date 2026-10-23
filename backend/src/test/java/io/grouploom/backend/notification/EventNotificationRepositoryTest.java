package io.grouploom.backend.notification;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventNotificationRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant CLAIM_UNTIL = NOW.plus(Duration.ofMinutes(2));

	@Autowired GroupRepository groups;
	@Autowired EventTypeRepository eventTypes;
	@Autowired EventRepository events;
	@Autowired EventNotificationRepository notifications;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager txManager;

	private Group group;
	private EventType type;
	private Event event;

	private Event event() {
		if (event == null) {
			group = groups.save(new Group("Club", NOW));
			type = eventTypes.save(new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW));
			Instant start = NOW.plus(Duration.ofDays(5));
			event = events.saveAndFlush(
					new Event(group, type, "Practice", null, null, start, start.plusSeconds(3600), "UTC", NOW));
		}
		return event;
	}

	private EventNotification notification(NotificationKind kind, Instant sendAt, Integer offset) {
		Event e = event();
		return notifications.saveAndFlush(new EventNotification(e, group, kind, sendAt, offset, null, null, NOW));
	}

	@Test
	void claimDue_returnsDueScheduledRowsAndMarksThemSending() {
		EventNotification due = notification(NotificationKind.INVITATION, NOW.minusSeconds(10), null);
		notification(NotificationKind.REMINDER, NOW.plusSeconds(60), null);

		List<UUID> claimed = notifications.claimDue(NOW, CLAIM_UNTIL, 10);

		assertThat(claimed).containsExactly(due.getId());
		assertThat(jdbc.queryForObject("select status from event_notifications where id = ?", String.class, due.getId()))
			.isEqualTo("SENDING");
		assertThat(jdbc.queryForObject("select claimed_until from event_notifications where id = ?", Timestamp.class,
				due.getId()).toInstant()).isEqualTo(CLAIM_UNTIL);
	}

	@Test
	void claimDue_skipsSentCancelledAndStillClaimedRows_andReclaimsExpiredOnes() {
		EventNotification sent = notification(NotificationKind.INVITATION, NOW.minusSeconds(10), null);
		jdbc.update("update event_notifications set status = 'SENT' where id = ?", sent.getId());
		EventNotification cancelled = notification(NotificationKind.INVITATION, NOW.minusSeconds(10), null);
		jdbc.update("update event_notifications set status = 'CANCELLED' where id = ?", cancelled.getId());
		EventNotification inFlight = notification(NotificationKind.INVITATION, NOW.minusSeconds(10), null);
		jdbc.update("update event_notifications set status = 'SENDING', claimed_until = ? where id = ?",
				Timestamp.from(NOW.plusSeconds(30)), inFlight.getId());
		EventNotification crashed = notification(NotificationKind.INVITATION, NOW.minusSeconds(10), null);
		jdbc.update("update event_notifications set status = 'SENDING', claimed_until = ? where id = ?",
				Timestamp.from(NOW.minusSeconds(1)), crashed.getId());

		assertThat(notifications.claimDue(NOW, CLAIM_UNTIL, 10)).containsExactly(crashed.getId());
	}

	@Test
	void claimDue_bumpsTheVersionSoAConcurrentUnscheduleFails() {
		EventNotification due = notification(NotificationKind.INVITATION, NOW.minusSeconds(10), null);
		long before = jdbc.queryForObject("select version from event_notifications where id = ?", Long.class, due.getId());

		notifications.claimDue(NOW, CLAIM_UNTIL, 10);

		assertThat(jdbc.queryForObject("select version from event_notifications where id = ?", Long.class, due.getId()))
			.isEqualTo(before + 1);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void claimDue_inTwoConcurrentTransactions_neverReturnsTheSameId() throws Exception {
		TransactionTemplate tx = new TransactionTemplate(txManager);
		try {
			tx.executeWithoutResult(s -> {
				notification(NotificationKind.INVITATION, NOW.minusSeconds(30), null);
				notification(NotificationKind.REMINDER, NOW.minusSeconds(20), null);
			});
			CountDownLatch firstHasClaimed = new CountDownLatch(1);
			CountDownLatch secondDone = new CountDownLatch(1);
			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<List<UUID>> first = pool.submit(() -> tx.execute(s -> {
					List<UUID> ids = notifications.claimDue(NOW, CLAIM_UNTIL, 1);
					firstHasClaimed.countDown();
					await(secondDone);
					return ids;
				}));
				await(firstHasClaimed);
				Future<List<UUID>> second = pool.submit(() -> tx.execute(s -> notifications.claimDue(NOW, CLAIM_UNTIL, 1)));
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
	void findAllByEventIdOrderBySendAtAsc_returnsTheEventsNotificationsInOrder() {
		EventNotification late = notification(NotificationKind.REMINDER, NOW.plusSeconds(500), null);
		EventNotification early = notification(NotificationKind.INVITATION, NOW.plusSeconds(10), null);

		assertThat(notifications.findAllByEventIdOrderBySendAtAsc(event.getId())).containsExactly(early, late);
	}

	@Test
	void findByIdAndEventIdAndGroupId_scopesByEventAndGroup() {
		EventNotification n = notification(NotificationKind.INVITATION, NOW, null);

		assertThat(notifications.findByIdAndEventIdAndGroupId(n.getId(), event.getId(), group.getId())).isPresent();
		assertThat(notifications.findByIdAndEventIdAndGroupId(n.getId(), event.getId(), UUID.randomUUID())).isEmpty();
		assertThat(notifications.findByIdAndEventIdAndGroupId(n.getId(), UUID.randomUUID(), group.getId())).isEmpty();
	}

	@Test
	void countAndExists_byEventAndStatus() {
		notification(NotificationKind.INVITATION, NOW, null);
		notification(NotificationKind.REMINDER, NOW, null);
		EventNotification sent = notification(NotificationKind.REMINDER, NOW, null);
		jdbc.update("update event_notifications set status = 'SENT' where id = ?", sent.getId());

		assertThat(notifications.countByEventIdAndStatus(event.getId(), NotificationStatus.SCHEDULED)).isEqualTo(2);
		assertThat(notifications.existsByEventIdAndStatus(event.getId(), NotificationStatus.SENT)).isTrue();
		assertThat(notifications.existsByEventIdAndStatus(event.getId(), NotificationStatus.CANCELLED)).isFalse();
	}

	@Test
	void deleteEvent_cascadesToItsNotifications() {
		notification(NotificationKind.INVITATION, NOW, null);

		jdbc.update("delete from events where id = ?", event.getId());

		assertThat(jdbc.queryForObject("select count(*) from event_notifications", Long.class)).isZero();
	}

	@Test
	void deleteGroup_cascadesToItsNotifications() {
		notification(NotificationKind.INVITATION, NOW, null);

		jdbc.update("delete from groups where id = ?", group.getId());

		assertThat(jdbc.queryForObject("select count(*) from event_notifications", Long.class)).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "SPAM,SCHEDULED,", "REMINDER,LOST,", "REMINDER,SCHEDULED,14", "REMINDER,SCHEDULED,43201" })
	void insert_withUnknownKindStatusOrOutOfRangeOffset_violatesCheckConstraints(String kind, String status,
			Integer offset) {
		notification(NotificationKind.INVITATION, NOW, null);

		assertThatThrownBy(() -> jdbc.update("insert into event_notifications "
				+ "(id, event_id, group_id, kind, status, send_at, offset_minutes) values (gen_random_uuid(), ?, ?, ?, ?, now(), ?)",
				event.getId(), group.getId(), kind, status, offset)).isInstanceOf(DataIntegrityViolationException.class);
	}

}
