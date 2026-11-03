package io.grouploom.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.grouploom.backend.TestcontainersConfiguration;

/** Demo mode is off by default: no seeding beans and no demo user. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemoDataDisabledTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void startup_demoNotEnabled_seedsNothing() {
		assertThat(context.getBeansOfType(DemoDataService.class)).isEmpty();
		assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from users where email = 'demo@example.com'", Long.class))
			.isZero();
	}

}
