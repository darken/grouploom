package io.grouploom.backend.membertoken;

import org.springframework.web.util.UriComponentsBuilder;

public final class UnsubscribeLinks {

	private UnsubscribeLinks() {
	}

	/**
	 * The one-click endpoint, built for the {@code List-Unsubscribe} header. Mail clients POST to it, so the token
	 * has to be in the query string; that is why it is a separate, pause-only token and never the preferences one.
	 */
	public static String url(String publicBaseUrl, String rawToken) {
		return UriComponentsBuilder.fromUriString(publicBaseUrl)
			.path("/api/public/unsubscribe")
			.queryParam("t", rawToken)
			.build()
			.toUriString();
	}

}
