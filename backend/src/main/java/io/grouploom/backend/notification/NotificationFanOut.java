package io.grouploom.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.grouploom.backend.config.AppProperties;

/**
 * Stage one of the pipeline: claim the notifications that are due, then expand each into outbox rows (the
 * existing dispatcher sends those). Claiming commits on its own; each notification is then processed in its
 * own transaction, so one failure never holds up the others.
 */
@Component
public class NotificationFanOut {

	private static final Logger log = LoggerFactory.getLogger(NotificationFanOut.class);

	private final EventNotificationRepository notifications;
	private final NotificationFanOutWorker worker;
	private final AppProperties properties;
	private final Clock clock;

	public NotificationFanOut(EventNotificationRepository notifications, NotificationFanOutWorker worker,
			AppProperties properties, Clock clock) {
		this.notifications = notifications;
		this.worker = worker;
		this.properties = properties;
		this.clock = clock;
	}

	/** Processes one batch of due notifications and returns how many were claimed. */
	public int fanOutDue() {
		AppProperties.Notifications config = properties.notifications();
		Instant now = clock.instant();
		List<UUID> ids = notifications.claimDue(now, now.plus(config.claimTimeout()), config.batchSize());
		for (UUID id : ids) {
			try {
				worker.process(id);
			}
			catch (RuntimeException e) {
				// The claim expires and the notification is picked up again; the unique index makes that safe.
				log.error("Could not fan out notification {}", id, e);
			}
		}
		return ids.size();
	}

}
