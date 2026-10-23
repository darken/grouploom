package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class EventTimeTextTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	@Test
	void range_forASingleDay_showsTheDateOnceWithTheZoneAbbreviation() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-07T11:00:00Z"), MADRID);

		assertThat(text).isEqualTo("Sat, Nov 7 · 10:00 – 12:00 CET");
	}

	@Test
	void range_acrossMidnight_showsBothDates() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T21:00:00Z"), Instant.parse("2026-11-08T01:00:00Z"), MADRID);

		assertThat(text).isEqualTo("Sat, Nov 7, 22:00 – Sun, Nov 8, 02:00 CET");
	}

	@Test
	void range_usesSummerAbbreviationsInSummer() {
		String text = EventTimeText.range(Instant.parse("2026-07-04T08:00:00Z"), Instant.parse("2026-07-04T09:00:00Z"), MADRID);

		assertThat(text).isEqualTo("Sat, Jul 4 · 10:00 – 11:00 CEST");
	}

	@Test
	void range_inUtc_saysUtc() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-07T10:30:00Z"),
				ZoneId.of("UTC"));

		assertThat(text).isEqualTo("Sat, Nov 7 · 09:00 – 10:30 UTC");
	}

	@Test
	void date_andClock_areFormattedInTheEventsZone() {
		Instant start = Instant.parse("2026-11-07T23:30:00Z");

		assertThat(EventTimeText.date(start, MADRID)).isEqualTo("Sun, Nov 8");
		assertThat(EventTimeText.clock(start, MADRID)).isEqualTo("00:30");
	}

	@Test
	void dayWord_onTheSameDayAsTheSendTime_isToday() {
		assertThat(EventTimeText.dayWord(Instant.parse("2026-11-07T18:00:00Z"), Instant.parse("2026-11-07T08:00:00Z"), MADRID))
			.isEqualTo("today");
	}

	@Test
	void dayWord_theDayAfterTheSendTime_isTomorrow() {
		assertThat(EventTimeText.dayWord(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-06T18:00:00Z"), MADRID))
			.isEqualTo("tomorrow");
	}

	@Test
	void dayWord_furtherAway_isTheDate() {
		assertThat(EventTimeText.dayWord(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-01T18:00:00Z"), MADRID))
			.isEqualTo("on Sat, Nov 7");
	}

	@Test
	void dayWord_comparesDaysInTheEventsZoneNotUtc() {
		// 23:30Z on the 6th is already the 7th in Madrid, so an event at 10:00 Madrid on the 7th is "today".
		assertThat(EventTimeText.dayWord(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-06T23:30:00Z"), MADRID))
			.isEqualTo("today");
	}

}
