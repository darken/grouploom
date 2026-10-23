package io.grouploom.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;

class EventTest {

	private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant START = Instant.parse("2026-06-02T10:00:00Z");

	private final Group group = new Group("Club", T0);
	private final EventType type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, T0);

	private Event event() {
		return new Event(group, type, "Practice", null, null, START, START.plusSeconds(3600), "UTC", T0);
	}

	@Test
	void cancel_setsTheTimestampAndMessage() {
		Event e = event();

		e.cancel("Closed", T0);

		assertThat(e.isCancelled()).isTrue();
		assertThat(e.getCancelledAt()).isEqualTo(T0);
		assertThat(e.getCancellationMessage()).isEqualTo("Closed");
	}

	@Test
	void update_whenCancelled_throws() {
		Event e = event();
		e.cancel(null, T0);

		assertThatThrownBy(() -> e.update(type, "New", null, null, START, START.plusSeconds(60), "UTC", T0))
			.isInstanceOf(IllegalStateException.class);
	}

}
