package io.grouploom.backend.preference;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.error.InvalidMemberTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * RFC 8058 one-click unsubscribe. Mail clients POST to the {@code List-Unsubscribe} URL with the body
 * {@code List-Unsubscribe=One-Click}; the body is ignored. {@code permitAll} at the URL level and authorized by the
 * {@code UNSUBSCRIBE} token in the query string.
 */
@RestController
@RequestMapping(path = "/public/unsubscribe", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicUnsubscribeController {

	private final UnsubscribeService service;

	public PublicUnsubscribeController(UnsubscribeService service) {
		this.service = service;
	}

	@PostMapping(consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE })
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Unsubscribe from event emails",
			description = "Pauses invitations for the member the link belongs to. Safe to repeat. The `List-Unsubscribe=One-Click` "
					+ "body mail clients send is accepted and ignored.")
	@ApiResponse(responseCode = "200", description = "Invitations are paused")
	@ApiResponse(responseCode = "401",
			description = "Invalid link (member-token.invalid) or expired link (member-token.expired)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public void unsubscribe(
			@Parameter(description = "The unsubscribe token from the email's List-Unsubscribe header") @RequestParam(name = "t", required = false) String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidMemberTokenException(InvalidMemberTokenException.INVALID);
		}
		service.unsubscribe(token);
	}

}
