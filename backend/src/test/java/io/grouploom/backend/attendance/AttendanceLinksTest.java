package io.grouploom.backend.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttendanceLinksTest {

	@Test
	void url_withoutAResponse_putsOnlyTheTokenInTheFragment() {
		assertThat(AttendanceLinks.url("https://app.example.com", "tok-1", null))
			.isEqualTo("https://app.example.com/m/attend#t=tok-1");
	}

	@Test
	void url_withGoing_addsTheChoiceToTheFragment() {
		assertThat(AttendanceLinks.url("https://app.example.com", "tok-1", AttendanceResponse.GOING))
			.isEqualTo("https://app.example.com/m/attend#t=tok-1&r=going");
	}

	@Test
	void url_withNotGoing_usesAHyphenatedChoice() {
		assertThat(AttendanceLinks.url("https://app.example.com/", "tok-1", AttendanceResponse.NOT_GOING))
			.isEqualTo("https://app.example.com/m/attend#t=tok-1&r=not-going");
	}

}
