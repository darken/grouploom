package io.grouploom.backend.config;

import java.time.Duration;

/** Builds {@link AppProperties} for unit tests, with the defaults the app would bind. */
public final class TestProperties {

	private TestProperties() {
	}

	public static AppProperties app(String publicBaseUrl, Duration linkTtl) {
		return new AppProperties(publicBaseUrl, new AppProperties.MemberLinks(linkTtl),
				new AppProperties.Mail("no-reply@grouploom.test", true, Duration.ofSeconds(5), 20, 5,
						Duration.ofMinutes(2), Duration.ofMinutes(5)));
	}

}
