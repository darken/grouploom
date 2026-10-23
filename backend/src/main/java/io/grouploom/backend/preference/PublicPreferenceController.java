package io.grouploom.backend.preference;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.preference.dto.PublicPreferencesResponse;
import io.grouploom.backend.preference.dto.UpdatePreferencesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/**
 * Reached from email links, so it is {@code permitAll} at the URL level and authorized by the member
 * token in {@code X-Member-Token}. An organizer's bearer token is never accepted in its place.
 */
@RestController
@RequestMapping(path = "/public/preferences", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicPreferenceController {

	static final String TOKEN_HEADER = "X-Member-Token";

	private final PreferenceService service;

	public PublicPreferenceController(PreferenceService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "Get a member's email preferences",
			description = "Authorized by the member link token, not by a user session.")
	@ApiResponse(responseCode = "401",
			description = "Invalid link (member-token.invalid) or expired link (member-token.expired)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public PublicPreferencesResponse get(
			@Parameter(name = TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
					description = "The token from the member's link") @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
		return service.get(requireToken(token));
	}

	@PutMapping
	@Operation(summary = "Update a member's email preferences",
			description = "Event types missing from the request keep their current choice.")
	@ApiResponse(responseCode = "400", description = "Unknown event type (code preference.unknown-event-type)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401",
			description = "Invalid link (member-token.invalid) or expired link (member-token.expired)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public PublicPreferencesResponse update(
			@Parameter(name = TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
					description = "The token from the member's link") @RequestHeader(value = TOKEN_HEADER, required = false) String token,
			@Valid @RequestBody UpdatePreferencesRequest request) {
		return service.update(requireToken(token), request);
	}

	// A missing header is a 401 like any other bad link (not a 400), so the page always shows the same message.
	private static String requireToken(String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidMemberTokenException(InvalidMemberTokenException.INVALID);
		}
		return token;
	}

}
