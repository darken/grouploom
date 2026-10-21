package io.grouploom.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.user.repository.UserRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

	@Autowired GroupRepository groups;
	@Autowired UserRepository users;

	@Test
	void save_withEmailDifferingOnlyByCase_violatesUniqueIndex() {
		Group group = groups.save(new Group("Club", Instant.now()));
		users.saveAndFlush(new User(group, "ada@example.com", "Ada", "h", Role.OWNER, Instant.now()));

		assertThatThrownBy(() -> users.saveAndFlush(new User(group, "ADA@Example.com", "Ada 2", "h", Role.ORGANIZER, Instant.now())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findByEmailIgnoreCase_withDifferentCase_findsUser() {
		Group group = groups.save(new Group("Club", Instant.now()));
		users.saveAndFlush(new User(group, "ada@example.com", "Ada", "h", Role.OWNER, Instant.now()));

		assertThat(users.findByEmailIgnoreCase("ADA@example.com")).isPresent();
		assertThat(users.existsByEmailIgnoreCase("Ada@Example.com")).isTrue();
	}

	@Test
	void findWithGroupById_loadsGroupEagerly() {
		Group group = groups.save(new Group("Club", Instant.now()));
		User saved = users.saveAndFlush(new User(group, "ada@example.com", "Ada", "h", Role.OWNER, Instant.now()));

		User found = users.findWithGroupById(saved.getId()).orElseThrow();

		assertThat(found.getGroup().getName()).isEqualTo("Club");
	}

}
