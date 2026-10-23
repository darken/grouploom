package io.grouploom.backend.membertoken;

import org.springframework.web.util.UriComponentsBuilder;

public final class PreferencesLinks {

	private PreferencesLinks() {
	}

	/** The token goes in the fragment so it never reaches server logs or Referer headers. */
	public static String url(String publicBaseUrl, String rawToken) {
		return UriComponentsBuilder.fromUriString(publicBaseUrl)
			.path("/m/preferences")
			.fragment("t=" + rawToken)
			.build()
			.toUriString();
	}

}
