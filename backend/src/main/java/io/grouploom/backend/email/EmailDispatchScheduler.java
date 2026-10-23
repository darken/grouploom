package io.grouploom.backend.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Integration tests turn this off and call {@link EmailDispatcher#dispatchBatch()} directly. */
@Component
@ConditionalOnProperty(name = "grouploom.mail.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class EmailDispatchScheduler {

	private final EmailDispatcher dispatcher;

	public EmailDispatchScheduler(EmailDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelayString = "${grouploom.mail.dispatch-interval:5s}")
	public void run() {
		dispatcher.dispatchBatch();
	}

}
