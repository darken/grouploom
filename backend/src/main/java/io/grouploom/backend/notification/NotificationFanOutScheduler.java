package io.grouploom.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Integration tests turn this off and call {@link NotificationFanOut#fanOutDue()} directly. */
@Component
@ConditionalOnProperty(name = "grouploom.notifications.fan-out-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationFanOutScheduler {

	private final NotificationFanOut fanOut;

	public NotificationFanOutScheduler(NotificationFanOut fanOut) {
		this.fanOut = fanOut;
	}

	@Scheduled(fixedDelayString = "${grouploom.notifications.fan-out-interval:30s}")
	public void run() {
		fanOut.fanOutDue();
	}

}
