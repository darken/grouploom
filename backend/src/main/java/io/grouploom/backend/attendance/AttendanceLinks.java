package io.grouploom.backend.attendance;

import org.springframework.web.util.UriComponentsBuilder;

public final class AttendanceLinks {

	private AttendanceLinks() {
	}

	/**
	 * The reply page. The token and the chosen answer go in the fragment, so neither reaches server logs or Referer
	 * headers, and opening the link only preselects the choice: the member confirms it on the page.
	 */
	public static String url(String publicBaseUrl, String rawToken, AttendanceResponse response) {
		String fragment = "t=" + rawToken;
		if (response != null) {
			fragment += "&r=" + (response == AttendanceResponse.GOING ? "going" : "not-going");
		}
		return UriComponentsBuilder.fromUriString(publicBaseUrl).path("/m/attend").fragment(fragment).build().toUriString();
	}

}
