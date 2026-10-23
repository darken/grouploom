package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailDispatchSchedulerTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(EmailDispatcher.class, () -> mock(EmailDispatcher.class))
		.withBean(EmailDispatchScheduler.class);

	@Test
	void scheduler_isRegisteredByDefault() {
		runner.run(context -> assertThat(context).hasSingleBean(EmailDispatchScheduler.class));
	}

	@Test
	void scheduler_isAbsentWhenTheDispatcherIsDisabled() {
		runner.withPropertyValues("grouploom.mail.dispatcher-enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(EmailDispatchScheduler.class));
	}

}
