package io.grouploom.backend.email;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.config.AppProperties;

/** Records the outcome of one send in its own short transaction, so no lock is held while sending. */
@Component
public class EmailOutboxRecorder {

	private final EmailOutboxRepository outbox;
	private final AppProperties properties;
	private final Clock clock;

	public EmailOutboxRecorder(EmailOutboxRepository outbox, AppProperties properties, Clock clock) {
		this.outbox = outbox;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public void sent(UUID id) {
		outbox.findById(id).ifPresent(e -> e.markSent(clock.instant()));
	}

	@Transactional
	public void retryLater(UUID id, String error) {
		outbox.findById(id).ifPresent(e -> e.scheduleRetry(clock.instant(), error, properties.mail().maxAttempts()));
	}

	@Transactional
	public void failed(UUID id, String error) {
		outbox.findById(id).ifPresent(e -> e.fail(clock.instant(), error));
	}

}
