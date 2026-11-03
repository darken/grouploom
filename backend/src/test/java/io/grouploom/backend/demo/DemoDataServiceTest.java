package io.grouploom.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.auth.AuthResult;
import io.grouploom.backend.auth.dto.LoginRequest;
import io.grouploom.backend.auth.service.AuthService;

/** Demo mode seeds one group at startup, the demo organizer can log in, and seeding again is a no-op. */
@SpringBootTest(properties = { "grouploom.demo.enabled=true", "grouploom.notifications.fan-out-enabled=false" })
@Import(TestcontainersConfiguration.class)
class DemoDataServiceTest {

	@Autowired
	DemoDataService demoData;

	@Autowired
	AuthService auth;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void startup_demoEnabled_demoUserCanLogIn() {
		AuthResult result = auth.login(new LoginRequest("demo@example.com", "grouploom-demo"));

		assertThat(result.refreshToken()).isNotBlank();
	}

	@Test
	void startup_demoEnabled_seedsDemoGroup() {
		UUID groupId = demoGroupId();

		assertThat(count("select count(*) from groups where id = ? and name = 'Riverside Running Club'", groupId))
			.isEqualTo(1);
		assertThat(count("select count(*) from event_types where group_id = ?", groupId)).isEqualTo(4);
		assertThat(count("select count(*) from event_types where group_id = ? and archived_at is not null", groupId))
			.isEqualTo(1);
		assertThat(count("select count(*) from members where group_id = ?", groupId)).isEqualTo(15);
		assertThat(count("select count(*) from events where group_id = ?", groupId)).isEqualTo(8);
		assertThat(count("select count(*) from events where group_id = ? and cancelled_at is not null", groupId))
			.isEqualTo(1);
		assertThat(count("select count(*) from attendances where group_id = ?", groupId)).isPositive();
		assertThat(count("select count(*) from attendances where group_id = ? and note is not null", groupId))
			.isPositive();
	}

	@Test
	void startup_demoEnabled_upcomingEventsAreInTheFuture() {
		UUID groupId = demoGroupId();
		Timestamp now = Timestamp.from(Instant.now());

		assertThat(count("select count(*) from events where group_id = ? and ends_at > ?", groupId, now)).isEqualTo(5);
		assertThat(count("select count(*) from events where group_id = ? and ends_at <= ?", groupId, now)).isEqualTo(3);
	}

	@Test
	void startup_demoEnabled_seedsPreferences() {
		UUID groupId = demoGroupId();

		assertThat(count("""
				select count(*) from member_preferences p join members m on m.id = p.member_id
				where m.group_id = ? and p.invitations_paused_at is not null""", groupId)).isEqualTo(1);
		assertThat(count("""
				select count(*) from member_event_type_opt_outs o join members m on m.id = o.member_id
				where m.group_id = ?""", groupId)).isEqualTo(1);
	}

	@Test
	void startup_demoEnabled_queuesNoEmail() {
		UUID groupId = demoGroupId();

		assertThat(count("select count(*) from email_outbox where group_id = ?", groupId)).isZero();
		assertThat(count("select count(*) from event_notifications where group_id = ?", groupId)).isZero();
	}

	@Test
	void seedIfMissing_alreadySeeded_createsNothing() {
		long members = count("select count(*) from members");
		long events = count("select count(*) from events");

		boolean seeded = demoData.seedIfMissing();

		assertThat(seeded).isFalse();
		assertThat(count("select count(*) from groups where name = 'Riverside Running Club'")).isEqualTo(1);
		assertThat(count("select count(*) from members")).isEqualTo(members);
		assertThat(count("select count(*) from events")).isEqualTo(events);
	}

	private UUID demoGroupId() {
		return jdbc.queryForObject("select group_id from users where email = 'demo@example.com'", UUID.class);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

}
