package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.grouploom.backend.email.EventTimeText.RelativeDay;

class EventTimeTextTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
	private static final Locale EN = Locale.ENGLISH;
	private static final Locale JA = Locale.JAPANESE;

	@Test
	void range_forASingleDay_showsTheDateOnceWithTheZoneAbbreviation() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-07T11:00:00Z"), MADRID, EN);

		assertThat(text).isEqualTo("Sat, Nov 7 · 10:00 – 12:00 CET");
	}

	@Test
	void range_acrossMidnight_showsBothDates() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T21:00:00Z"), Instant.parse("2026-11-08T01:00:00Z"), MADRID, EN);

		assertThat(text).isEqualTo("Sat, Nov 7, 22:00 – Sun, Nov 8, 02:00 CET");
	}

	@Test
	void range_usesSummerAbbreviationsInSummer() {
		String text = EventTimeText.range(Instant.parse("2026-07-04T08:00:00Z"), Instant.parse("2026-07-04T09:00:00Z"), MADRID, EN);

		assertThat(text).isEqualTo("Sat, Jul 4 · 10:00 – 11:00 CEST");
	}

	@Test
	void range_inUtc_saysUtc() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-07T10:30:00Z"),
				ZoneId.of("UTC"), EN);

		assertThat(text).isEqualTo("Sat, Nov 7 · 09:00 – 10:30 UTC");
	}

	@Test
	void date_andClock_areFormattedInTheEventsZone() {
		Instant start = Instant.parse("2026-11-07T23:30:00Z");

		assertThat(EventTimeText.date(start, MADRID, EN)).isEqualTo("Sun, Nov 8");
		assertThat(EventTimeText.clock(start, MADRID, EN)).isEqualTo("00:30");
	}

	@Test
	void relativeDay_onTheSameDayAsTheSendTime_isToday() {
		assertThat(EventTimeText.relativeDay(Instant.parse("2026-11-07T18:00:00Z"), Instant.parse("2026-11-07T08:00:00Z"), MADRID))
			.isEqualTo(RelativeDay.TODAY);
	}

	@Test
	void relativeDay_theDayAfterTheSendTime_isTomorrow() {
		assertThat(EventTimeText.relativeDay(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-06T18:00:00Z"), MADRID))
			.isEqualTo(RelativeDay.TOMORROW);
	}

	@Test
	void relativeDay_furtherAway_isOnADate() {
		assertThat(EventTimeText.relativeDay(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-01T18:00:00Z"), MADRID))
			.isEqualTo(RelativeDay.ON_DATE);
	}

	@Test
	void relativeDay_comparesDaysInTheEventsZoneNotUtc() {
		// 23:30Z on the 6th is already the 7th in Madrid, so an event at 10:00 Madrid on the 7th is "today".
		assertThat(EventTimeText.relativeDay(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-06T23:30:00Z"), MADRID))
			.isEqualTo(RelativeDay.TODAY);
	}

	@Test
	void range_inJapanese_forASingleDay_usesTheJapaneseDateAndATilde() {
		// 10:00 – 12:00 in Tokyo on Saturday 2 November 2030.
		String text = EventTimeText.range(Instant.parse("2030-11-02T01:00:00Z"), Instant.parse("2030-11-02T03:00:00Z"), TOKYO, JA);

		assertThat(text).isEqualTo("11月2日(土) 10:00〜12:00 JST");
	}

	@Test
	void range_inJapanese_acrossMidnight_showsBothDates() {
		String text = EventTimeText.range(Instant.parse("2030-11-02T13:00:00Z"), Instant.parse("2030-11-02T17:00:00Z"), TOKYO, JA);

		assertThat(text).isEqualTo("11月2日(土) 22:00〜11月3日(日) 02:00 JST");
	}

	@Test
	void range_inJapanese_keepsTheEventsZoneAbbreviation() {
		String text = EventTimeText.range(Instant.parse("2026-11-07T09:00:00Z"), Instant.parse("2026-11-07T11:00:00Z"), MADRID, JA);

		assertThat(text).isEqualTo("11月7日(土) 10:00〜12:00 CET");
	}

	@Test
	void date_andClock_inJapanese() {
		Instant start = Instant.parse("2026-11-07T23:30:00Z");

		assertThat(EventTimeText.date(start, MADRID, JA)).isEqualTo("11月8日(日)");
		assertThat(EventTimeText.clock(start, MADRID, JA)).isEqualTo("00:30");
	}

}
