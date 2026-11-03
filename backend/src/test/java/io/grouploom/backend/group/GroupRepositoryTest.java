package io.grouploom.backend.group;

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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GroupRepositoryTest {

	@Autowired GroupRepository groups;
	@Autowired JdbcTemplate jdbc;

	@Test
	void save_withLanguage_readsItBack() {
		Group saved = groups.saveAndFlush(new Group("Club", Language.JA, Instant.now()));

		assertThat(groups.findById(saved.getId()).orElseThrow().getLanguage()).isEqualTo(Language.JA);
		assertThat(jdbc.queryForObject("select language from groups where id = ?", String.class, saved.getId()))
			.isEqualTo("ja");
	}

	@Test
	void changeLanguage_updatesTheStoredValue() {
		Group saved = groups.saveAndFlush(new Group("Club", Language.EN, Instant.now()));

		saved.changeLanguage(Language.JA);
		groups.saveAndFlush(saved);

		assertThat(jdbc.queryForObject("select language from groups where id = ?", String.class, saved.getId()))
			.isEqualTo("ja");
	}

	@Test
	void insert_withoutLanguage_defaultsToEnglish() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into groups (id, name) values (?, 'Old club')", id);

		assertThat(groups.findById(id).orElseThrow().getLanguage()).isEqualTo(Language.EN);
	}

	@Test
	void insert_withUnsupportedLanguage_isRejectedByTheDatabase() {
		assertThatThrownBy(() -> jdbc.update("insert into groups (id, name, language) values (?, 'Club', 'fr')", UUID.randomUUID()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
